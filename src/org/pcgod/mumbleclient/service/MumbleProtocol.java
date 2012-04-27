package org.pcgod.mumbleclient.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import net.sf.mumble.MumbleProto.ChannelRemove;
import net.sf.mumble.MumbleProto.ChannelState;
import net.sf.mumble.MumbleProto.CodecVersion;
import net.sf.mumble.MumbleProto.CryptSetup;
import net.sf.mumble.MumbleProto.Reject;
import net.sf.mumble.MumbleProto.ServerSync;
import net.sf.mumble.MumbleProto.TextMessage;
import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.service.audio.AudioOutput;
import org.pcgod.mumbleclient.service.audio.AudioOutputHost;
import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.Message;
import org.pcgod.mumbleclient.service.model.User;

import android.content.Context;

import com.google.protobuf.ByteString;

public class MumbleProtocol {
	public enum MessageType {
		Version, UDPTunnel, Authenticate, Ping, Reject, ServerSync, ChannelRemove, ChannelState, UserRemove, UserState, BanList, TextMessage, PermissionDenied, ACL, QueryUsers, CryptSetup, ContextActionAdd, ContextAction, UserList, VoiceTarget, PermissionQuery, CodecVersion, UserStats, RequestBlob, ServerConfig
	}

	public static final int UDPMESSAGETYPE_UDPVOICECELTALPHA = 0;
	public static final int UDPMESSAGETYPE_UDPPING = 1;
	public static final int UDPMESSAGETYPE_UDPVOICESPEEX = 2;
	public static final int UDPMESSAGETYPE_UDPVOICECELTBETA = 3;

	public static final int CODEC_NOCODEC = -1;
	public static final int CODEC_ALPHA = UDPMESSAGETYPE_UDPVOICECELTALPHA;
	public static final int CODEC_BETA = UDPMESSAGETYPE_UDPVOICECELTBETA;

	public static final int SAMPLE_RATE = 48000;
	public static final int FRAME_SIZE = SAMPLE_RATE / 100;

	/**
	 * The time window during which the last successful UDP ping must have been
	 * transmitted. If the time since the last successful UDP ping is greater
	 * than this treshold the connection falls back on TCP tunneling.
	 *
	 * NOTE: This is the time when the last successfully received ping was SENT
	 * by the client.
	 *
	 * 6000 gives 1 second reply-time as the ping interval is 5000 seconds
	 * currently.
	 */
	public static final int UDP_PING_TRESHOLD = 6000;

	private static final MessageType[] MT_CONSTANTS = MessageType.class.getEnumConstants();

	public Map<Integer, Channel> channels = new HashMap<Integer, Channel>();
	public Map<Integer, User> users = new HashMap<Integer, User>();
	public Channel currentChannel = null;
	public User currentUser = null;
	public boolean canSpeak = true;
	public int codec = CODEC_NOCODEC;
	private final AudioOutputHost audioHost;
	private final Context ctx;

	private AudioOutput ao;
	private Thread audioOutputThread;
	private Thread pingThread;

	private final MumbleProtocolHost host;
	private final MumbleConnection conn;

	private boolean stopped = false;

	public MumbleProtocol(
		final MumbleProtocolHost host,
		final AudioOutputHost audioHost,
		final MumbleConnection connection,
		final Context ctx) {
		this.host = host;
		this.audioHost = audioHost;
		this.conn = connection;
		this.ctx = ctx;

		this.host.setSynchronized(false);
	}

	public final void joinChannel(final int channelId) {
		final UserState.Builder us = UserState.newBuilder();
		us.setSession(currentUser.session);
		us.setChannelId(channelId);
		conn.sendTcpMessage(MessageType.UserState, us);
	}

	public void processTcp(final short type, final byte[] buffer)
		throws IOException {
		if (stopped) {
			return;
		}

		if (type >= MT_CONSTANTS.length) {
			Globals.logWarn(this, String.format("Unknown message type %s", type));
			return;
		}

		final MessageType t = MT_CONSTANTS[type];

		Channel channel;
		User user;

		switch (t) {
		case UDPTunnel:
			processUdp(buffer, buffer.length);
			break;
		case Ping:
			// ignore
			break;
		case CodecVersion:
			final boolean oldCanSpeak = canSpeak;
			final CodecVersion codecVersion = CodecVersion.parseFrom(buffer);
			codec = CODEC_NOCODEC;
			if (codecVersion.hasAlpha() &&
				codecVersion.getAlpha() == Globals.CELT_VERSION) {
				codec = CODEC_ALPHA;
			} else if (codecVersion.hasBeta() &&
					   codecVersion.getBeta() == Globals.CELT_VERSION) {
				codec = CODEC_BETA;
			}
			canSpeak = canSpeak && (codec != CODEC_NOCODEC);

			if (canSpeak != oldCanSpeak) {
				host.currentUserUpdated();
			}

			break;
		case Reject:
			final Reject reject = Reject.parseFrom(buffer);
			final String errorString = String.format(
				"Connection rejected: %s",
				reject.getReason());
			host.setError(errorString);
			Globals.logError(this, String.format(
				"Received Reject message: %s",
				reject.getReason()));
			break;
		case ServerSync:
			final ServerSync ss = ServerSync.parseFrom(buffer);

			// We do some things that depend on being executed only once here
			// so for now assert that there won't be multiple ServerSyncs.
			Assert.assertNull("A second ServerSync received.", currentUser);

			currentUser = findUser(ss.getSession());
			currentUser.isCurrent = true;
			currentChannel = currentUser.getChannel();

			pingThread = new Thread(new PingThread(conn), "Ping");
			pingThread.start();
			Globals.logDebug(this, ">>> " + t);

			ao = new AudioOutput(ctx, audioHost);
			audioOutputThread = new Thread(ao, "audio output");
			audioOutputThread.start();

			final UserState.Builder usb = UserState.newBuilder();
			usb.setSession(currentUser.session);
			conn.sendTcpMessage(MessageType.UserState, usb);

			host.setSynchronized(true);

			host.currentChannelChanged();
			host.currentUserUpdated();
			break;
		case ChannelState:
			final ChannelState cs = ChannelState.parseFrom(buffer);
			channel = findChannel(cs.getChannelId());
			if (channel != null) {
				if (cs.hasName()) {
					channel.name = cs.getName();
				}
				host.channelUpdated(channel);
				break;
			}

			// New channel
			channel = new Channel();
			channel.id = cs.getChannelId();
			channel.name = cs.getName();
            channel.hasParent = cs.hasParent();
            channel.hasPosition = cs.hasPosition();
            channel.parent = cs.getParent();
            channel.position = cs.getPosition();
			channels.put(channel.id, channel);
			host.channelAdded(channel);
			break;
		case ChannelRemove:
			final ChannelRemove cr = ChannelRemove.parseFrom(buffer);
			channel = findChannel(cr.getChannelId());
			channel.removed = true;
			channels.remove(channel.id);
			host.channelRemoved(channel.id);
			break;
		case UserState:
			final UserState us = UserState.parseFrom(buffer);
			user = findUser(us.getSession());

			boolean added = false;
			boolean currentUserUpdated = false;
			boolean channelUpdated = false;

			if (user == null) {
				user = new User();
				user.session = us.getSession();
				users.put(user.session, user);
				added = true;
			}

			if (us.hasSelfDeaf() || us.hasSelfMute()) {
				if (us.getSelfDeaf()) {
					user.userState = User.USERSTATE_DEAFENED;
				} else if (us.getSelfMute()) {
					user.userState = User.USERSTATE_MUTED;
				} else {
					user.userState = User.USERSTATE_NONE;
				}
			}

			if (us.hasMute()) {
				user.muted = us.getMute();
				user.userState = user.muted ? User.USERSTATE_MUTED
					: User.USERSTATE_NONE;
			}

			if (us.hasDeaf()) {
				user.deafened = us.getDeaf();
				user.muted |= user.deafened;
				user.userState = user.deafened ? User.USERSTATE_DEAFENED
					: (user.muted ? User.USERSTATE_MUTED : User.USERSTATE_NONE);
			}

			if (us.hasSuppress()) {
				user.userState = us.getSuppress() ? User.USERSTATE_MUTED
					: User.USERSTATE_NONE;
			}

			if (us.hasName()) {
				user.name = us.getName();
			}

			if (added || us.hasChannelId()) {
				user.setChannel(channels.get(us.getChannelId()));
				channelUpdated = true;
			}

			// If this is the current user, do extra updates on local state.
			if (currentUser != null && us.getSession() == currentUser.session) {
				if (us.hasMute() || us.hasSuppress()) {
					// TODO: Check the logic
					// Currently Mute+Suppress true -> Either of them false results
					// in canSpeak = true
					if (us.hasMute()) {
						canSpeak = (codec != CODEC_NOCODEC) && !us.getMute();
					}
					if (us.hasSuppress()) {
						canSpeak = (codec != CODEC_NOCODEC) &&
								   !us.getSuppress();
					}
				}

				currentUserUpdated = true;
			}

			if (channelUpdated) {
				host.channelUpdated(user.getChannel());
			}

			if (added) {
				host.userAdded(user);
			} else {
				host.userUpdated(user);
			}

			if (currentUserUpdated) {
				host.currentUserUpdated();
			}
			if (currentUserUpdated && channelUpdated) {
				currentChannel = user.getChannel();
				host.currentChannelChanged();
			}
			break;
		case UserRemove:
			final UserRemove ur = UserRemove.parseFrom(buffer);
			user = findUser(ur.getSession());
			users.remove(user.session);

			// Remove the user from the channel as well.
			user.getChannel().userCount--;

			host.channelUpdated(user.getChannel());
			host.userRemoved(user.session);
			break;
		case TextMessage:
			handleTextMessage(TextMessage.parseFrom(buffer));
			break;
		case CryptSetup:
			final CryptSetup cryptsetup = CryptSetup.parseFrom(buffer);

			Globals.logDebug(this, "MumbleConnection: CryptSetup");

			if (cryptsetup.hasKey() && cryptsetup.hasClientNonce() &&
				cryptsetup.hasServerNonce()) {
				// Full key setup
				conn.cryptState.setKeys(
					cryptsetup.getKey().toByteArray(),
					cryptsetup.getClientNonce().toByteArray(),
					cryptsetup.getServerNonce().toByteArray());
			} else if (cryptsetup.hasServerNonce()) {
				// Server syncing its nonce to us.
				Globals.logDebug(this, "MumbleConnection: Server sending nonce");
				conn.cryptState.setServerNonce(cryptsetup.getServerNonce().toByteArray());
			} else {
				// Server wants our nonce.
				Globals.logDebug(this,
					"MumbleConnection: Server requesting nonce");
				final CryptSetup.Builder nonceBuilder = CryptSetup.newBuilder();
				nonceBuilder.setClientNonce(ByteString.copyFrom(conn.cryptState.getClientNonce()));
				conn.sendTcpMessage(MessageType.CryptSetup, nonceBuilder);
			}
			break;
		default:
			Globals.logWarn(this, "unhandled message type " + t);
		}
	}

	public void processUdp(final byte[] buffer, final int length) {
		if (stopped) {
			return;
		}

		final int type = buffer[0] >> 5 & 0x7;
		if (type == UDPMESSAGETYPE_UDPPING) {
			final long timestamp = ((long) (buffer[1] & 0xFF) << 56) |
								   ((long) (buffer[2] & 0xFF) << 48) |
								   ((long) (buffer[3] & 0xFF) << 40) |
								   ((long) (buffer[4] & 0xFF) << 32) |
								   ((long) (buffer[5] & 0xFF) << 24) |
								   ((long) (buffer[6] & 0xFF) << 16) |
								   ((long) (buffer[7] & 0xFF) << 8) |
								   ((buffer[8] & 0xFF));

			conn.refreshUdpLimit(timestamp + UDP_PING_TRESHOLD);
		} else {
			processVoicePacket(buffer);
		}
	}

	public final void sendChannelTextMessage(
		final String message,
		final Channel channel) {
		final TextMessage.Builder tmb = TextMessage.newBuilder();
		tmb.addChannelId(channel.id);
		tmb.setMessage(message);
		conn.sendTcpMessage(MessageType.TextMessage, tmb);

		final Message msg = new Message();
		msg.timestamp = System.currentTimeMillis();
		msg.message = message;
		msg.channel = channel;
		msg.direction = Message.DIRECTION_SENT;
		host.messageSent(msg);
	}

	public void stop() {
		stopped = true;
		stopThreads();
	}

	private Channel findChannel(final int id) {
		return channels.get(id);
	}

	private User findUser(final int session_) {
		return users.get(session_);
	}

	private void handleTextMessage(final TextMessage ts) {
		User u = null;
		if (ts.hasActor()) {
			u = findUser(ts.getActor());
		}

		final Message msg = new Message();
		msg.timestamp = System.currentTimeMillis();
		msg.message = ts.getMessage();
		msg.actor = u;
		msg.direction = Message.DIRECTION_RECEIVED;
		msg.channelIds = ts.getChannelIdCount();
		msg.treeIds = ts.getTreeIdCount();
		host.messageReceived(msg);
	}

	private void processVoicePacket(final byte[] buffer) {
		final int type = buffer[0] >> 5 & 0x7;
		final int flags = buffer[0] & 0x1f;

		// There is no speex support...
		if (type != UDPMESSAGETYPE_UDPVOICECELTALPHA &&
			type != UDPMESSAGETYPE_UDPVOICECELTBETA) {
			return;
		}

		// Don't try to decode the unsupported codec version.
		if (type != codec) {
			return;
		}

		final PacketDataStream pds = new PacketDataStream(buffer);
		// skip type / flags
		pds.skip(1);
		final long uiSession = pds.readLong();

		final User u = findUser((int) uiSession);
		if (u == null) {
			Globals.logError(this, "User session " + uiSession + " not found!");

			// This might happen if user leaves while there are still UDP packets
			// en route to the clients. In this case we should just ignore these
			// packets.
			return;
		}

		// Rewind the packet. Otherwise consumers are confusing to implement.
		pds.rewind();
		ao.addFrameToBuffer(u, pds, flags);
	}

	private void stopThreads() {
		if (ao != null) {
			ao.stop();
			try {
				audioOutputThread.join();
			} catch (final InterruptedException e) {
				Globals.logWarn(this,
					"Interrupted while waiting for audio thread to end",
					e);
			}
		}

		if (pingThread != null) {
			pingThread.interrupt();
			try {
				pingThread.join();
			} catch (final InterruptedException e) {
				Globals.logError(this,
					"Interrupted while waiting for ping thread to end",
					e);
			}
		}
	}

}
