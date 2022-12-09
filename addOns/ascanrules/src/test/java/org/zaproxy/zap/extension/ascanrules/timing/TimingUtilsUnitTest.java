/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2022 The ZAP Development Team
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
package org.zaproxy.zap.extension.ascanrules.timing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.arrayContaining;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link TimingUtils}.
 * The unit tests that test checkTimingDependence won't actually throw an IOException,
 * they are simply marked that way to simplify the test code.
 * */
class TimingUtilsUnitTest {

    private static final double CORRELATION_ERROR_RANGE = 0.1;
    private static final double SLOPE_ERROR_RANGE = 0.2;

    private final Random rand = new Random();
    private int timesCalled;

    @BeforeEach
    void init() {
        timesCalled = 0;
    }

    @Test
    // verifies that an incrementing sequence of delays is automatically generated
    void shouldAutoIncrementDelay() throws IOException {
        // Given
        ArrayList<Double> generatedDelays = new ArrayList<>();
        // When
        boolean result =
                TimingUtils.checkTimingDependence(
                        5,
                        15,
                        (x) -> {
                            generatedDelays.add(x);
                            return x;
                        },
                        CORRELATION_ERROR_RANGE,
                        SLOPE_ERROR_RANGE);
        // Then
        assertThat(result, is(true));
        assertThat(generatedDelays.toArray(), arrayContaining(1.0, 2.0, 3.0, 4.0, 5.0));
    }

    @Test
    // incrementing sequence of delays is automatically generated but then loops back to 1
    void shouldAutoIncrementThenLoop() throws IOException {
        // Given
        ArrayList<Double> generatedDelays = new ArrayList<>();
        // When
        boolean result =
                TimingUtils.checkTimingDependence(
                        10,
                        20,
                        (x) -> {
                            generatedDelays.add(x);
                            return x;
                        },
                        CORRELATION_ERROR_RANGE,
                        SLOPE_ERROR_RANGE);
        // Then
        assertThat(result, is(true));
        assertThat(generatedDelays.toArray(), arrayContaining(1.0, 2.0, 3.0, 4.0, 5.0, 1.0, 2.0, 1.0, 1.0));
    }

    @Test
    // detect the case where the endpoint isn't injectable and responds quickly
    // should only send 1 request and then bail
    void shouldGiveUpQuicklyWhenNotInjectable() throws IOException {
        // When
        boolean result =
                TimingUtils.checkTimingDependence(
                        5,
                        15,
                        // respond with a low time
                        (x) -> {
                            timesCalled += 1;
                            return 0.5;
                        },
                        CORRELATION_ERROR_RANGE,
                        SLOPE_ERROR_RANGE);
        // Then
        assertThat(result, is(false));
        assertThat(timesCalled, is(1));
    }

    @Test
    // detect the case when the wait time is long, but not necessarily injectable
    // should only send 2-3 requests and then bail early
    void shouldGiveUpQuicklyWhenSlowButNotInjectable() throws IOException {
        // When
        boolean result =
                TimingUtils.checkTimingDependence(
                        5,
                        15,
                        // source of small error
                        (x) -> {
                            timesCalled += 1;
                            return 10 + rand.nextDouble() * 0.5;
                        },
                        CORRELATION_ERROR_RANGE,
                        SLOPE_ERROR_RANGE);
        // Then
        assertThat(result, is(false));
        assertThat(timesCalled, lessThanOrEqualTo(3));
    }

    @Test
    // verify the typical use case: detect correlation with relatively small noise
    void shouldDetectDependenceWithSmallError() throws IOException {
        // When
        boolean result =
                TimingUtils.checkTimingDependence(
                        5, 15,
                        // source of small error
                        (x) -> x + rand.nextDouble() * 0.5,
                        CORRELATION_ERROR_RANGE,
                        SLOPE_ERROR_RANGE);
        // Then
        assertThat(result, is(true));
    }
}
