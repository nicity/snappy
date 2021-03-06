/*
 * Copyright (C) 2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.iq80.snappy;

import com.google.common.io.Files;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class SnappyTest
{
    private static final File TEST_DATA_DIR = new File("testdata");
    private RandomGenerator randomGenerator = new RandomGenerator(0.5);

    @Test
    public void testByteForByteOutputSyntheticData()
            throws Exception
    {
        for (int i = 1; i < 65 * 1024; i++) {
            try {
                verifyCompression(i);
            }
            catch (Error e) {
                Assert.fail(i + " byte block", e);
            }
        }
    }

    @Test
    public void testIssue30() throws IOException {
        byte[] compressed = {107,115,47,85,112,-127,83,30,49,9,0,67,38,-99,41,9,25,26,102,20,81,116,-2,8,21,98,8,21,-18,-94,0,5,-94,41,69,-2,60,22,98,60,22,-50,-96,0,109,-88,0,-111,-2,-42,1,-2,-42,1,61,-42,41,38,12,-61,4,0,-110,-70,-107,0,98,-36,2,28,114,101,115,117,108,116,47,82,5,7,101,-75,18,-91,17,12,72,101,97,100,26,-39,44,102,27,0,54,-76,5,-2,-48,4,-107,-48,-126,-109,0,41,6,8,-23,3,0,-58,-77};
        try {
            Snappy.uncompress(compressed, 0, compressed.length);
            Assert.fail();
        } catch (CorruptionException ignored) {
        }
    }

    @Test
    public void testByteForByteTestData()
            throws Exception
    {
        for (File testFile : getTestFiles()) {
            byte[] data = Files.toByteArray(testFile);
            try {
                verifyCompression(data, 0, data.length);
            }
            catch (Throwable e) {
                Assert.fail("Testdata: " + testFile.getName(), e);

            }
        }
    }

    private void verifyCompression(int size)
            throws Exception
    {
        byte[] input = randomGenerator.data;
        int position = randomGenerator.getNextPosition(size);

        verifyCompression(input, position, size);
    }

    private static void verifyCompression(byte[] input, int position, int size)
            throws Exception
    {
        byte[] nativeCompressed = new byte[org.xerial.snappy.Snappy.maxCompressedLength(size)];
        byte[] javaCompressed = new byte[Snappy.maxCompressedLength(size)];

        int nativeCompressedSize = org.xerial.snappy.Snappy.compress(
                input,
                position,
                size,
                nativeCompressed,
                0);

        int javaCompressedSize = Snappy.compress(
                input,
                position,
                size,
                javaCompressed,
                0);

        // verify outputs are exactly the same
        String failureMessage = "Invalid compressed output for input size " + size + " at offset " + position;
        if (!SnappyInternalUtils.equals(javaCompressed, 0, nativeCompressed, 0, nativeCompressedSize)) {
            if (nativeCompressedSize < 100) {
                Assert.assertEquals(
                        Arrays.toString(Arrays.copyOf(javaCompressed, nativeCompressedSize)),
                        Arrays.toString(Arrays.copyOf(nativeCompressed, nativeCompressedSize)),
                        failureMessage
                );
            }
            else {
                Assert.fail(failureMessage);
            }
        }
        Assert.assertEquals(javaCompressedSize, nativeCompressedSize);

        // verify the contents can be uncompressed
        byte[] uncompressed = new byte[size];
        Snappy.uncompress(javaCompressed, 0, javaCompressedSize, uncompressed, 0);

        if (!SnappyInternalUtils.equals(uncompressed, 0, input, position, size)) {
            Assert.fail("Invalid uncompressed output for input size " + size + " at offset " + position);
        }
    }

    public static class RandomGenerator
    {
        public final byte[] data;
        public int position;

        public RandomGenerator(double compressionRatio)
        {
            // We use a limited amount of data over and over again and ensure
            // that it is larger than the compression window (32KB), and also
            // large enough to serve all typical value sizes we want to write.
            Random rnd = new Random(301);
            data = new byte[1048576 + 100];
            for (int i = 0; i < 1048576; i += 100) {
                // Add a short fragment that is as compressible as specified ratio
                System.arraycopy(compressibleData(rnd, compressionRatio, 100), 0, data, i, 100);
            }
        }

        public int getNextPosition(int length)
        {
            if (position + length > data.length) {
                position = 0;
                assert (length < data.length);
            }
            int result = position;
            position += length;
            return result;
        }

        private static byte[] compressibleData(Random random, double compressionRatio, int length)
        {
            int raw = (int) (length * compressionRatio);
            if (raw < 1) {
                raw = 1;
            }
            byte[] rawData = generateRandomData(random, raw);

            // Duplicate the random data until we have filled "length" bytes
            byte[] dest = new byte[length];
            for (int i = 0; i < length; ) {
                int chunkLength = Math.min(rawData.length, length - i);
                System.arraycopy(rawData, 0, dest, i, chunkLength);
                i += chunkLength;
            }
            return dest;
        }

        private static byte[] generateRandomData(Random random, int length)
        {
            byte[] rawData = new byte[length];
            for (int i = 0; i < rawData.length; i++) {
                rawData[i] = (byte) random.nextInt(256);
            }
            return rawData;
        }
    }

    static File[] getTestFiles()
    {
        File[] testFiles = TEST_DATA_DIR.listFiles();
        Assert.assertTrue(testFiles != null && testFiles.length > 0, "No test files at " + TEST_DATA_DIR.getAbsolutePath());
        return testFiles;
    }
}
