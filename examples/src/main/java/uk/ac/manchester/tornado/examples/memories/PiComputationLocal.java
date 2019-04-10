/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples.memories;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.examples.reductions.ConfigurationReduce;
import uk.ac.manchester.tornado.examples.reductions.Stats;

public class PiComputationLocal {

    public static void computePi(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 1; i < input.length; i++) {
            float value = (float) (Math.pow(-1, i + 1) / (2 * i - 1));
            result[0] += value + input[i];
        }
    }

    public void run(int size) {
        float[] input = new float[size];

        int numGroups = 1;
        if (size > 256) {
            numGroups = size / 256;
        }

        float[] result = ConfigurationReduce.allocResultArray(numGroups);
        Arrays.fill(result, 0.0f);

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", PiComputationLocal::computePi, input, result)
            .streamOut(result);
        //@formatter:on

        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < ConfigurationReduce.MAX_ITERATIONS; i++) {

            IntStream.range(0, size).sequential().forEach(idx -> {
                input[idx] = 0;
            });

            long start = System.nanoTime();
            task.execute();
            long end = System.nanoTime();

            for (int j = 1; j < result.length; j++) {
                result[0] += result[j];
            }
            final float piValue = result[0] * 4;
            System.out.println("PI VALUE: " + piValue);
            timers.add((end - start));
        }

        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));
    }

    public static void main(String[] args) {
        int inputSize = 8192;
        if (args.length > 0) {
            inputSize = Integer.parseInt(args[0]);
        }
        System.out.print("Size = " + inputSize + " ");
        new PiComputationLocal().run(inputSize);
    }
}
