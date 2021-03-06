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

import org.junit.Test;

import jcuda.driver.CUdeviceptr;

/**
 * Tests for the vector comparison methods
 */
public class TestVecDoubleComparison extends AbstractTestVecDouble
{
    @Test
    public void testLt()
    {
        runTest(new AbstractCoreDouble("lt")
        {
            @Override
            protected double computeHostElement(
                double x, double y, double scalar)
            {
                return x<y?1.0:0.0;
            }
            
            @Override
            protected void computeDevice(long n, CUdeviceptr result,
                CUdeviceptr x, CUdeviceptr y, double scalar)
            {
                VecDouble.lt(n, result, x, y);
            }
        });
    }

    @Test
    public void testLte()
    {
        runTest(new AbstractCoreDouble("lte")
        {
            @Override
            protected double computeHostElement(
                double x, double y, double scalar)
            {
                return x<=y?1.0:0.0;
            }
            
            @Override
            protected void computeDevice(long n, CUdeviceptr result,
                CUdeviceptr x, CUdeviceptr y, double scalar)
            {
                VecDouble.lte(n, result, x, y);
            }
        });
    }

    @Test
    public void testEq()
    {
        runTest(new AbstractCoreDouble("eq")
        {
            @Override
            protected double computeHostElement(
                double x, double y, double scalar)
            {
                return x==y?1.0:0.0;
            }
            
            @Override
            protected void computeDevice(long n, CUdeviceptr result,
                CUdeviceptr x, CUdeviceptr y, double scalar)
            {
                VecDouble.eq(n, result, x, y);
            }
        });
    }

    @Test
    public void testGte()
    {
        runTest(new AbstractCoreDouble("gte")
        {
            @Override
            protected double computeHostElement(
                double x, double y, double scalar)
            {
                return x>=y?1.0:0.0;
            }
            
            @Override
            protected void computeDevice(long n, CUdeviceptr result,
                CUdeviceptr x, CUdeviceptr y, double scalar)
            {
                VecDouble.gte(n, result, x, y);
            }
        });
    }

    @Test
    public void testGt()
    {
        runTest(new AbstractCoreDouble("gt")
        {
            @Override
            protected double computeHostElement(
                double x, double y, double scalar)
            {
                return x>y?1.0:0.0;
            }
            
            @Override
            protected void computeDevice(long n, CUdeviceptr result,
                CUdeviceptr x, CUdeviceptr y, double scalar)
            {
                VecDouble.gt(n, result, x, y);
            }
        });
    }

    
    @Test
    public void testNe()
    {
        runTest(new AbstractCoreDouble("ne")
        {
            @Override
            protected double computeHostElement(
                double x, double y, double scalar)
            {
                return x!=y?1.0:0.0;
            }
            
            @Override
            protected void computeDevice(long n, CUdeviceptr result,
                CUdeviceptr x, CUdeviceptr y, double scalar)
            {
                VecDouble.ne(n, result, x, y);
            }
        });
    }

    
}
