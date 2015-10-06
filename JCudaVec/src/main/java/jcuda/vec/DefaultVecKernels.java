/*
 * JCudaVec - Vector operations for JCuda 
 * http://www.jcuda.org
 *
 * Copyright (c) 2013-2015 Marco Hutter - http://www.jcuda.org
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package jcuda.vec;

import static jcuda.driver.JCudaDriver.cuCtxCreate;
import static jcuda.driver.JCudaDriver.cuCtxGetCurrent;
import static jcuda.driver.JCudaDriver.cuDeviceGet;
import static jcuda.driver.JCudaDriver.cuDeviceGetAttribute;
import static jcuda.driver.JCudaDriver.cuInit;
import static jcuda.driver.JCudaDriver.cuLaunchKernel;
import static jcuda.driver.JCudaDriver.cuModuleGetFunction;
import static jcuda.driver.JCudaDriver.cuModuleLoadDataEx;
import static jcuda.driver.JCudaDriver.cuModuleUnload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import jcuda.CudaException;
import jcuda.Pointer;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdevice_attribute;
import jcuda.driver.CUfunction;
import jcuda.driver.CUmodule;
import jcuda.driver.CUresult;
import jcuda.driver.CUstream;

/**
 * Default implementation of {@link VecKernels}
 */
final class DefaultVecKernels implements VecKernels
{
    /**
     * The module from which the kernels (i.e. the CUfunctions)
     * are obtained
     */
    private final CUmodule module;

    /**
     * The prefix that should be added to a kernel name in order to build
     * the name for the CUfunction
     */
    private final String kernelNamePrefix;

    /**
     * The suffix that should be added to a kernel name in order to build
     * the name for the CUfunction
     */
    private final String kernelNameSuffix;
    
    /**
     * The mapping from kernel names to CUfunctions
     */
    private final Map<String, CUfunction> functions;
    
    /**
     * The block dimension, in x-direction, that should be used for the calls
     */
    private int blockDimX;
    
    /**
     * The device number for which a context will be created if
     * no existing context can be used. 
     * 
     * TODO The device number and context handling have to be reviewed
     */
    private static final int deviceNumber = 0;
    
    /**
     * The stream that should be used for the kernel calls
     * 
     * TODO The stream can not be set yet
     */
    private CUstream stream;
    
    /**
     * Creates a new kernel set that allows calling the functions that 
     * are contained in the CUDA module that is read from a PTX file.
     *  
     * @param ptxInputStream The input stream for the CUDA module
     * @param kernelNameType The type for the kernels, either "float"
     * or "double"
     * @param kernelNamePrefix The prefix that should be added to a 
     * kernel name in order to build the name for the CUfunction
     * @param kernelNameSuffix The suffix that should be added to a 
     * kernel name in order to build the name for the CUfunction
     */
    DefaultVecKernels(
    	String kernelNameType,
    	String kernelNamePrefix,
    	String kernelNameSuffix)
    {
        this.kernelNamePrefix = kernelNamePrefix;
        this.kernelNameSuffix = kernelNameSuffix;
        
        initCUDA();
        
        this.blockDimX = getMaxBlockDimX();

        this.module = new CUmodule();
        
        String modelString = System.getProperty("sun.arch.data.model");
        int ccMajor = getComputeCapabilityMajor();
        String ccString = "20";
        if (ccMajor > 2)
        {
            ccString = "30";
        }
        String ptxFileName = "/kernels/JCudaVec_kernels_" + 
            kernelNameType + "_" + modelString + "_cc" + ccString + ".ptx";
        
        //System.out.println("Loading "+ptxFileName);
        
        byte ptxData[] = loadData(ptxFileName);
        
        checkResult(cuModuleLoadDataEx(module, Pointer.to(ptxData), 
            0, new int[0], Pointer.to(new int[0])));

        this.functions = new LinkedHashMap<String, CUfunction>();
    }

    /**
     * Obtain the CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_X of the current device
     * 
     * @return The maximum block dimension, in x-direction
     */
    private static int getMaxBlockDimX()
    {
        CUdevice device = new CUdevice();
        checkResult(cuDeviceGet(device, deviceNumber));
        int maxBlockDimX[] =  {0};
        cuDeviceGetAttribute(maxBlockDimX, 
            CUdevice_attribute.CU_DEVICE_ATTRIBUTE_MAX_BLOCK_DIM_X, 
            device);
        return maxBlockDimX[0];
    }
    
    /**
     * Obtain the CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR of the 
     * current device
     * 
     * @return The major version number part of the compute capability
     */
    private static int getComputeCapabilityMajor()
    {
        CUdevice device = new CUdevice();
        checkResult(cuDeviceGet(device, deviceNumber));
        int ccMajor[] =  {0};
        cuDeviceGetAttribute(ccMajor, 
            CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, 
            device);
        return ccMajor[0];
    }
    
    /**
     * Initializes the JCuda driver API. Then it will try to attach to the 
     * current CUDA context. If no active CUDA context exists, then it will 
     * try to create one, for the device which is specified by the current 
     * deviceNumber.
     * 
     * @throws CudaException If it is neither possible to attach to an 
     * existing context, nor to create a new context.
     */
    private static void initCUDA()
    {
        checkResult(cuInit(0));

        // Try to obtain the current context
        CUcontext context = new CUcontext();
        checkResult(cuCtxGetCurrent(context));
        
        // If the context is 'null', then a new context
        // has to be created.
        CUcontext nullContext = new CUcontext(); 
        if (context.equals(nullContext))
        {
            createContext();
        }
    }
    
    /**
     * Tries to create a context for device 'deviceNumber'.
     * 
     * @throws CudaException If the device can not be 
     * accessed or the context can not be created
     */
    private static void createContext()
    {
        CUdevice device = new CUdevice();
        checkResult(cuDeviceGet(device, deviceNumber));
        CUcontext context = new CUcontext();
        checkResult(cuCtxCreate(context, 0, device));
    }
    
    /**
     * If the given result is not CUresult.CUDA_SUCCESS, then this method
     * throws a CudaException with the error message for the given result.
     * 
     * @param cuResult The result
     * @throws CudaException if the result is not CUresult.CUDA_SUCCESS
     */
    private static void checkResult(int cuResult)
    {
        if (cuResult != CUresult.CUDA_SUCCESS)
        {
            throw new CudaException(CUresult.stringFor(cuResult));
        }
    }
    
    /**
     * Reads the data from a file resource with the given name, and returns 
     * it as a 0-terminated byte array. 
     * 
     * @param ptxFileName The name of the file to read
     * @return The data from the file
     * @throws CudaException If there is an IO error
     */
    private static byte[] loadData(String ptxFileName)
    {
        InputStream ptxInputStream = null;
        try
        {
            ptxInputStream = 
                DefaultVecKernels.class.getResourceAsStream(ptxFileName);
            if (ptxInputStream != null)
            {
                return loadData(ptxInputStream);
            }
            else
            {
                throw new CudaException(
                    "Could not initialize the kernels: " +
                    "Resource "+ptxFileName+" not found");
            }
        }
        finally
        {
            if (ptxInputStream != null)
            {
                try
                {
                    ptxInputStream.close();
                }
                catch (IOException e)
                {
                    throw new CudaException(
                        "Could not initialize the kernels", e);
                }
            }
        }
        
    }
    
    /**
     * Reads the data from the given inputStream and returns it as
     * a 0-terminated byte array. The caller is responsible to 
     * close the given stream.
     * 
     * @param inputStream The inputStream to read
     * @return The data from the inputStream
     * @throws CudaException If there is an IO error
     */
    private static byte[] loadData(InputStream inputStream)
    {
        ByteArrayOutputStream baos = null;
        try
        {
            baos = new ByteArrayOutputStream();
            byte buffer[] = new byte[8192];
            while (true)
            {
                int read = inputStream.read(buffer);
                if (read == -1)
                {
                    break;
                }
                baos.write(buffer, 0, read);
            }
            baos.write('\0');
            baos.flush();
            return baos.toByteArray();
        }
        catch (IOException e)
        {
            throw new CudaException(
                "Could not load data", e);
        }
        finally
        {
            if (baos != null)
            {
                try
                {
                    baos.close();
                }
                catch (IOException e)
                {
                    throw new CudaException(
                        "Could not close output", e);
                }
            }
        }
        
    }
    
    @Override
	public void call(String name, long workSize, Object ... arguments) 
	{
    	CUfunction function = obtainFunction(name);
    	Pointer kernelParameters = setupKernelParameters(arguments);
        callKernel(workSize, function, kernelParameters);
	}

    /**
     * Obtain the CUfunction for the kernel that is identified with the 
     * given name, loading it from the module if necessary.
     * 
     * @param name The name of the kernel
     * @return The CUfunction for the kernel
     */
    private CUfunction obtainFunction(String name)
    {
    	CUfunction function = functions.get(name);
        if (function == null)
        {
            function = new CUfunction();
            checkResult(cuModuleGetFunction(function, module, 
            	kernelNamePrefix+name+kernelNameSuffix));        
        }
        return function;
    }
    
    /**
     * Create a pointer to the given arguments that can be used as
     * the parameters for a kernel launch.
     * 
     * @param args The arguments
     * @return The pointer for the kernel arguments 
     * @throws NullPointerException If one of the given arguments is 
     * <code>null</code>
     * @throws CudaException If one of the given arguments has a type
     * that can not be passed to a kernel (that is, a type that is
     * neither primitive nor a {@link Pointer})
     */
    private Pointer setupKernelParameters(Object ... args)
    {
        Pointer kernelParameters[] = new Pointer[args.length];
        for (int i=0; i<args.length; i++)
        {
            Object arg = args[i];
            if (arg == null)
            {
                throw new NullPointerException("Argument "+i+" is null");
            }
            if (arg instanceof Pointer)
            {
                Pointer argPointer = (Pointer)arg;
                Pointer pointer = Pointer.to(argPointer);
                kernelParameters[i] = pointer;
            }
            else if (arg instanceof Byte)
            {
                Byte value = (Byte)arg;
                Pointer pointer = Pointer.to(new byte[]{value});
                kernelParameters[i] = pointer;
            }
            else if (arg instanceof Short)
            {
                Short value = (Short)arg;
                Pointer pointer = Pointer.to(new short[]{value});
                kernelParameters[i] = pointer;
            }
            else if (arg instanceof Integer)
            {
                Integer value = (Integer)arg;
                Pointer pointer = Pointer.to(new int[]{value});
                kernelParameters[i] = pointer;
            }
            else if (arg instanceof Long)
            {
                Long value = (Long)arg;
                Pointer pointer = Pointer.to(new long[]{value});
                kernelParameters[i] = pointer;
            }
            else if (arg instanceof Float)
            {
                Float value = (Float)arg;
                Pointer pointer = Pointer.to(new float[]{value});
                kernelParameters[i] = pointer;
            }
            else if (arg instanceof Double)
            {
                Double value = (Double)arg;
                Pointer pointer = Pointer.to(new double[]{value});
                kernelParameters[i] = pointer;
            }
            else
            {
                throw new CudaException(
                    "Type "+arg.getClass()+" may not be passed to a function");
            }
        }
        return Pointer.to(kernelParameters);
    }
	
    /**
     * Call the given CUDA function with the given parameters
     * 
     * @param workSize The global work size
     * @param function The CUDA function
     * @param kernelParameters The kernel parameters
     */
    private void callKernel(long workSize, CUfunction function, 
        Pointer kernelParameters)
    {
        int gridDimX = (int)Math.ceil((double)workSize / blockDimX);
        checkResult(cuLaunchKernel(function,
            gridDimX,  1, 1,
            blockDimX, 1, 1,
            0, stream,
            kernelParameters, null));
    }

    @Override
    public void shutdown()
    {
        cuModuleUnload(module);
    }
}