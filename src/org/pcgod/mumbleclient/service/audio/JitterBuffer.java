package org.pcgod.mumbleclient.service.audio;

class JitterBuffer {
	private class TimingBuffer {
		int filled;
		int curr_count;
		int[] timing = new int[MAX_TIMINGS];
		short[] counts = new short[MAX_TIMINGS];

		public void add(final short timing_) {
			if (filled >= MAX_TIMINGS && timing_ >= timing[filled - 1]) {
				curr_count++;
				return;
			}

			int pos = 0;
			while (pos < filled && timing_ >= timing[pos]) {
				++pos;
			}
			if (pos < filled) {
				int move_size = filled - pos;
				if (filled == MAX_TIMINGS) {
					move_size -= 1;
				}

				System.arraycopy(timing, pos, timing, pos + 1, move_size);
				System.arraycopy(counts, pos, counts, pos + 1, move_size);
			}
			timing[pos] = timing_;
			counts[pos] = (short) curr_count;
			++curr_count;
			if (filled < MAX_TIMINGS) {
				++filled;
			}
		}
	}

	private static final int MAX_TIMINGS = 40;
	private static final int JITTER_MAX_BUFFER_SIZE = 100;
	private static final int TOP_DELAY = 40;
	private static final int MAX_BUFFERS = 3;

	private int timestamp;
	/** Estimated time the next get() will be called */
	private int next_stop;

	/**
	 * Amount of data we think is still buffered by the application (timestamp
	 * units)
	 */
	private int buffered;

	/** Packets stored in the buffer */
	private final JitterBufferPacket[] packets = new JitterBufferPacket[JITTER_MAX_BUFFER_SIZE];
	/**
	 * Packet arrival time (0 means it was late, even though it's a valid
	 * timestamp)
	 */
	private final int[] arrival = new int[JITTER_MAX_BUFFER_SIZE];

	/** Size of the steps when adjusting buffering (timestamp units) */
	private final int delay_step;
	/** Size of the packet loss concealment "units" */
	private final int concealment_size;
	/** True if state was just reset */
	private boolean reset_state;
	/** How many frames we want to keep in the buffer (lower bound) */
	private int buffer_margin;
	/** An interpolation is requested by updateDelay() */
	private int interp_requested;
	/** Whether to automatically adjust the delay at any time */
	private boolean auto_adjust;

	/** Storing arrival time of latest frames so we can compute some stats */
	private TimingBuffer[] timeBuffers = new TimingBuffer[MAX_BUFFERS];

	/** Total window over which the late frames are counted */
	private int window_size;
	/** Sub-window size for faster computation */
	private int subwindow_size;
	/** Latency equivalent of losing one percent of packets */
	private int latency_tradeoff;
	/** Latency equivalent of losing one percent of packets (automatic default) */
	private int auto_tradeoff;

	/** Number of consecutive lost packets */
	private int lost;

	public JitterBuffer(final int step_size) {
		delay_step = step_size;
		concealment_size = step_size;
		auto_adjust = true;

		setMaxLateRate(4);
		reset();
	}

	public JitterBufferPacket get(final int desired_span) {
		if (reset_state) {
			boolean found = false;
			int oldest = 0;
			for (int i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
				if (packets[i] != null &&
						(!found || packets[i].timestamp < oldest)) {
					oldest = packets[i].timestamp;
					found = true;
				}
			}
			if (found) {
				reset_state = false;
				timestamp = oldest;
				next_stop = oldest;
			} else {
//				Log.i("mumble.jb", "JITTER_BUFFER_MISSING");
				return null;
			}
		}

		if (interp_requested != 0) {
			timestamp += interp_requested;
			buffered = interp_requested - desired_span;
			interp_requested = 0;
//			Log
//					.i("mumble.jb",
//							"JITTER_BUFFER_INSERTION - Deferred interpolate");
			return null;
		}

		int i;
		for (i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
			if (packets[i] != null &&
					packets[i].timestamp == timestamp &&
					packets[i].timestamp + packets[i].span >= timestamp +
							desired_span) {
				break;
			}
		}

		if (i == JITTER_MAX_BUFFER_SIZE) {
			for (i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
				if (packets[i] != null &&
						packets[i].timestamp <= timestamp &&
						packets[i].timestamp + packets[i].span >= timestamp +
								desired_span) {
					break;
				}
			}
		}

		if (i == JITTER_MAX_BUFFER_SIZE) {
			for (i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
				if (packets[i] != null && packets[i].timestamp <= timestamp &&
						packets[i].timestamp + packets[i].span > timestamp) {
					break;
				}
			}
		}

		if (i == JITTER_MAX_BUFFER_SIZE) {
			boolean found = false;
			int best_time = 0;
			int best_span = 0;
			int besti = 0;
			for (i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
				if (packets[i] != null &&
						packets[i].timestamp < timestamp + desired_span &&
						packets[i].timestamp >= timestamp) {
					if (!found ||
							packets[i].timestamp < best_time ||
							(packets[i].timestamp == best_time && packets[i].span > best_span)) {
						best_time = packets[i].timestamp;
						best_span = packets[i].span;
						besti = i;
						found = true;
					}
				}
			}

			if (found) {
				i = besti;
			}
		}

		if (i != JITTER_MAX_BUFFER_SIZE) {
			lost = 0;
			if (arrival[i] != 0) {
				updateTimings((short) (packets[i].timestamp - arrival[i] - buffer_margin));
			}

			final JitterBufferPacket packet = packets[i];
			packets[i] = null;
			timestamp = packet.timestamp + packet.span;
			buffered = packet.span - desired_span;

			return packet;
		}

		++lost;

		final short opt = computeOptimalDelay();

		if (opt < 0) {
			shiftTimings((short) -opt);

			buffered = -opt - desired_span;
//			Log.i("mumble.jb",
//					"JITTER_BUFFER_INSERTION - Forced to interpolate");
		} else {
			final int tmp_desired_span = Math.min(desired_span,
					concealment_size);
			timestamp += tmp_desired_span;
			buffered = tmp_desired_span - tmp_desired_span;
//			Log.i("mumble.jb", "JITTER_BUFFER_MISSING - Normal loss");
		}
		return null;
	}

	public int getAvailable() {
		int count = 0;
		for (int i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
			if (packets[i] != null && timestamp <= packets[i].timestamp) {
				++count;
			}
		}
		return count;
	}

	public int getTimestamp() {
		return timestamp;
	}

	public void put(final JitterBufferPacket packet) {
		boolean late = false;

		if (!reset_state) {
			for (int i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
				if (packets[i] != null &&
						packets[i].timestamp + packets[i].span <= timestamp) {
					packets[i] = null;
				}
			}
		}

		if (!reset_state && packet.timestamp <= next_stop) {
			updateTimings((short) (packet.timestamp - next_stop - buffer_margin));
			late = true;
		}

		if (lost > 20) {
			reset();
		}

		if (reset_state ||
				packet.timestamp + packet.span + delay_step >= timestamp) {
			int i;
			for (i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
				if (packets[i] == null) {
					break;
				}
			}
			if (i == JITTER_MAX_BUFFER_SIZE) {
				int earliest = packets[0].timestamp;
				i = 0;
				for (int j = 0; j < JITTER_MAX_BUFFER_SIZE; ++j) {
					if (packets[i] != null || packets[j].timestamp < earliest) {
						earliest = packets[j].timestamp;
						i = j;
					}
				}
				packets[i] = null;
			}

			packets[i] = packet;

			if (reset_state || late) {
				arrival[i] = 0;
			} else {
				arrival[i] = next_stop;
			}
		}
	}

	public void reset() {
		for (int i = 0; i < JITTER_MAX_BUFFER_SIZE; ++i) {
			packets[i] = null;
		}

		timestamp = 0;
		next_stop = 0;
		reset_state = true;
		lost = 0;
		buffered = 0;
		auto_tradeoff = 32000;

		timeBuffers = new TimingBuffer[MAX_BUFFERS];
		for (int i = 0; i < MAX_BUFFERS; ++i) {
			timeBuffers[i] = new TimingBuffer();
		}
	}

	public void setMargin(final int margin) {
		buffer_margin = margin;
	}

	public void setMaxLateRate(final int max_late_rate) {
		window_size = 100 * TOP_DELAY / max_late_rate;
		subwindow_size = window_size / MAX_BUFFERS;
	}

	public void tick() {
		if (auto_adjust) {
			_updateDelay();
		}

		if (buffered >= 0) {
			next_stop = timestamp - buffered;
		} else {
			next_stop = timestamp;
//			Log.w("mumble.jb", "jitter buffer sees negative buffering, your code might be broken. Value is " + buffered);
		}
		buffered = 0;
	}

	public short updateDelay() {
		auto_adjust = false;

		return _updateDelay();
	}

	private short _updateDelay() {
		final short opt = computeOptimalDelay();

		if (opt != 0) {
			shiftTimings((short) -opt);
			timestamp += opt;
			if (opt < 0) {
				interp_requested = -opt;
			}
		}
		return opt;
	}

	private short computeOptimalDelay() {
		short opt = 0;
		int best_cost = 0x7fffffff;
		boolean penalty_taken = false;

		int tot_count = 0;
		for (int i = 0; i < MAX_BUFFERS; ++i) {
			tot_count += timeBuffers[i].curr_count;
		}
		if (tot_count == 0) {
			return 0;
		}

		float late_factor;
		if (latency_tradeoff != 0) {
			late_factor = latency_tradeoff * 100.0f / tot_count;
		} else {
			late_factor = auto_tradeoff * window_size / tot_count;
		}

		final int[] pos = new int[MAX_BUFFERS];
		for (int i = 0; i < MAX_BUFFERS; ++i) {
			pos[i] = 0;
		}

		int worst = 0;
		int best = 0;
		for (int i = 0; i < TOP_DELAY; ++i) {
			int next = -1;
			int latest = 32767;
			for (int j = 0; j < MAX_BUFFERS; ++j) {
				if (pos[j] < timeBuffers[j].filled &&
						timeBuffers[j].timing[pos[j]] < latest) {
					next = j;
					latest = timeBuffers[j].timing[pos[j]];
				}
			}

			int late = 0;
			if (next != -1) {
				if (i == 0) {
					worst = latest;
				}
				best = latest;
				latest = Math.min(latest, delay_step);
				++pos[next];
				final int cost = (int) (-latest + late_factor * late);

				if (cost < best_cost) {
					best_cost = cost;
					opt = (short) latest;
				}
			} else {
				break;
			}

			++late;
			if (latest >= 0 && !penalty_taken) {
				penalty_taken = true;
				late += 4;
			}
		}

		final int deltaT = best - worst;
		auto_tradeoff = 1 + deltaT / TOP_DELAY;

		if (tot_count < TOP_DELAY && opt > 0) {
			return 0;
		}

		return opt;
	}

	private void shiftTimings(final short amount) {
		for (int i = 0; i < MAX_BUFFERS; ++i) {
			for (int j = 0; j < timeBuffers[i].filled; ++j) {
				timeBuffers[i].timing[j] += amount;
			}
		}
	}

	private void updateTimings(final short timing) {
		if (timeBuffers[0].curr_count >= subwindow_size) {
			final TimingBuffer tmp = timeBuffers[MAX_BUFFERS - 1];
			for (int i = MAX_BUFFERS - 1; i >= 1; --i) {
				timeBuffers[i] = timeBuffers[i - 1];
			}
			timeBuffers[0] = tmp;
			timeBuffers[0].curr_count = 0;
			timeBuffers[0].filled = 0;
		}
		timeBuffers[0].add(timing);
	}
}
