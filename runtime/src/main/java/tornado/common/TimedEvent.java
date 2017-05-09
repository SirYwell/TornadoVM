/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.common;

public class TimedEvent {
	protected final long start;
	protected final long stop;
	
	public TimedEvent(final long t0, final long t1) {
		start = t0;
		stop = t1;
	}

	public long getNanoTime() {
		return stop - start;
	}

	public long getStart() {
		return start;
	}

	public long getStop() {
		return stop;
	}

	public double getTime() {
		return 1e-9 * getNanoTime();
	}
	
}