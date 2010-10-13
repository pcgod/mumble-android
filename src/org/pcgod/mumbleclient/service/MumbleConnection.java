package org.pcgod.mumbleclient.service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import net.sf.mumble.MumbleProto.Authenticate;
import net.sf.mumble.MumbleProto.ChannelRemove;
import net.sf.mumble.MumbleProto.ChannelState;
import net.sf.mumble.MumbleProto.CodecVersion;
import net.sf.mumble.MumbleProto.CryptSetup;
import net.sf.mumble.MumbleProto.Reject;
import net.sf.mumble.MumbleProto.ServerSync;
import net.sf.mumble.MumbleProto.TextMessage;
import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;
import net.sf.mumble.MumbleProto.Version;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.service.MumbleConnectionHost.ConnectionState;
import org.pcgod.mumbleclient.service.audio.AudioOutput;
import org.pcgod.mumbleclient.service.audio.AudioOutputHost;
import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.Message;
import org.pcgod.mumbleclient.service.model.User;

import android.util.Log;

import com.google.protobuf.MessageLite;

/**
 * The main mumble client connection
 *
 * Maintains connection to the server and implements the low level
 * communication protocol
 *
 * @author pcgod
 */
public class MumbleConnection implements Runnable {
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

	private static final MessageType[] MT_CONSTANTS = MessageType.class
			.getEnumConstants();

	private static final int protocolVersion = (1 << 16) | (2 << 8) |
			(3 & 0xFF);

	private static final int supportedCodec = 0x8000000b;

	public Map<Integer, Channel> channels = new HashMap<Integer, Channel>();
	public Map<Integer, User> users = new HashMap<Integer, User>();
	public Channel currentChannel = null;
	public User currentUser = null;
	public boolean canSpeak = true;
	public int codec = CODEC_NOCODEC;
	private final MumbleConnectionHost connectionHost;
	private final AudioOutputHost audioHost;

	private DataInputStream in;
	private DataOutputStream out;
	private Thread pingThread;
	private boolean disconnecting = false;

	private final String host;
	private final int port;
	private final String username;
	private final String password;

	private AudioOutput ao;
	private Thread audioOutputThread;
	private Thread readingThread;
	private final Object stateLock = new Object();
	private final CryptState cryptState = new CryptState();
	private String errorString;

	/**
	 * Constructor for new connection thread.
	 *
	 * This thread should be started shortly after construction. Construction
	 * sets the connection state for the host to "Connecting" even if the actual
	 * connection won't be attempted until the thread has been started.
	 *
	 * This is to combat an issue where the Service is asked to connect and the
	 * thread is started but the thread isn't given execution time before another
	 * activity checks for connection state and finds out the service is in
	 * Disconnected state.
	 *
	 * @param connectionHost_ Host interface for this Connection
	 * @param host_           Mumble server host address
	 * @param port_           Mumble server port
	 * @param username_       Username
	 * @param password_       Server password
	 */
	public MumbleConnection(
		final MumbleConnectionHost connectionHost_,
		final AudioOutputHost audioHost_,
		final String host_,
		final int port_,
		final String username_,
		final String password_) {
		connectionHost = connectionHost_;
		audioHost = audioHost_;
		host = host_;
		port = port_;
		username = username_;
		password = password_;

		connectionHost.setConnectionState(ConnectionState.Connecting);
	}

	public final void disconnect() {
		disconnecting = true;
		synchronized (stateLock) {
			if (readingThread != null) {
				readingThread.interrupt();
				readingThread = null;
			}

			connectionHost.setConnectionState(ConnectionState.Disconnecting);
			stateLock.notifyAll();
		}
	}

	public String getError() {
		final String error = errorString;
		errorString = null;
		return error;
	}

	public final boolean isConnectionAlive() {
		return !disconnecting;
	}

	public final boolean isSameServer(final String host_, final int port_,
			final String username_, final String password_) {
		return host.equals(host_) && port == port_ &&
				username.equals(username_) && password.equals(password_);
	}

	public final void joinChannel(final int channelId) {
		final UserState.Builder us = UserState.newBuilder();
		us.setSession(currentUser.session);
		us.setChannelId(channelId);
		try {
			sendMessage(MessageType.UserState, us);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public final void run() {
		try {
			SSLSocket socket_;

			synchronized (stateLock) {
				final SSLContext ctx_ = SSLContext.getInstance("TLS");
				ctx_.init(null,
						  new TrustManager[] { new LocalSSLTrustManager() },
						  null);
				final SSLSocketFactory factory = ctx_.getSocketFactory();
				socket_ = (SSLSocket) factory.createSocket(host, port);
				socket_.setUseClientMode(true);
				socket_.setEnabledProtocols(new String[] { "TLSv1" });
				socket_.startHandshake();

				connectionHost.setConnectionState(ConnectionState.Connected);
			}

			handleProtocol(socket_);

			// Clean connection state that might have been initialized.
			// Do this before closing the socket as the threads could use it.
			if (ao != null) {
				ao.stop();
				audioOutputThread.join();
			}

			socket_.close();
		} catch (final NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (final KeyManagementException e) {
			e.printStackTrace();
		} catch (final UnknownHostException e) {
			errorString = String.format("Host \"%s\" unknown", host);
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}

		synchronized (stateLock) {
			connectionHost.setConnectionState(ConnectionState.Disconnected);
		}
	}

	public final void sendChannelTextMessage(final String message) {
		final TextMessage.Builder tmb = TextMessage.newBuilder();
		tmb.addChannelId(currentChannel.id);
		tmb.setMessage(message);
		try {
			sendMessage(MessageType.TextMessage, tmb);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		final Message msg = new Message();
		msg.timestamp = System.currentTimeMillis();
		msg.message = message;
		msg.channel = currentChannel;
		msg.direction = Message.DIRECTION_SENT;
		connectionHost.messageSent(msg);
	}

	public final void sendMessage(final MessageType t,
			final MessageLite.Builder b) throws IOException {
		final MessageLite m = b.build();
		final short type = (short) t.ordinal();
		final int length = m.getSerializedSize();

		synchronized (out) {
			out.writeShort(type);
			out.writeInt(length);
			m.writeTo(out);
		}

		if (t != MessageType.Ping) {
			Log.i(Globals.LOG_TAG, "<<< " + t);
		}
	}

	public final void sendUdpTunnelMessage(final byte[] buffer, final int length)
		throws IOException {
		final short type = (short) MessageType.UDPTunnel.ordinal();

		synchronized (out) {
			out.writeShort(type);
			out.writeInt(length);
			out.write(buffer, 0, length);
		}
	}

	private Channel findChannel(final int id) {
		return channels.get(id);
	}

	private User findUser(final int session_) {
		return users.get(session_);
	}

	private void handleProtocol(final Socket socket_) throws IOException,
			InterruptedException {
		synchronized (stateLock) {
			if (disconnecting) {
				return;
			}

			out = new DataOutputStream(socket_.getOutputStream());
			in = new DataInputStream(socket_.getInputStream());

			final Version.Builder v = Version.newBuilder();
			v.setVersion(protocolVersion);
			v.setRelease("javalib 0.0.1-dev");

			final Authenticate.Builder a = Authenticate.newBuilder();
			a.setUsername(username);
			a.setPassword(password);
			a.addCeltVersions(supportedCodec);

			sendMessage(MessageType.Version, v);
			sendMessage(MessageType.Authenticate, a);

			if (disconnecting) {
				return;
			}

			connectionHost.setConnectionState(ConnectionState.Connected);
		}

		// Process the stream in separate thread so we can interrupt it if necessary
		// without interrupting the whole connection thread and thus allowing us to
		// disconnect cleanly.
		readingThread = new Thread(new Runnable() {
			public void run() {
				byte[] msg = null;
				try {
					while (socket_.isConnected() && !disconnecting) {

						// Do the socket read outside of any lock as this is blocking operation
						// which might take a while and must be interruptible.
						// Interrupt itself is synchronized through stateLock.
						final short type = in.readShort();
						final int length = in.readInt();
						if (msg == null || msg.length != length) {
							msg = new byte[length];
						}
						in.readFully(msg);

						// Message processing can be done inside stateLock as it shouldn't involve
						// slow network operations.
						synchronized (stateLock) {
							processMsg(MT_CONSTANTS[type], msg);
						}
					}
				} catch (final IOException ex) {
					Log.e(Globals.LOG_TAG, ex.toString());
				} finally {
					synchronized (stateLock) {
						connectionHost.setConnectionState(ConnectionState.Disconnecting);
						disconnecting = true;

						// The thread is dying so null it. This prevents the waiting loop from
						// spotting that the thread might still be alive briefly after notifyAll.
						readingThread = null;
						stateLock.notifyAll();
					}
				}
			}
		});

		readingThread.start();

		synchronized (stateLock) {
			while (readingThread != null && readingThread.isAlive()) {
				stateLock.wait();
			}
			readingThread = null;
		}
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
		connectionHost.messageReceived(msg);
	}

	private void processMsg(final MessageType t, final byte[] buffer)
			throws IOException {

		Channel channel;
		User user;

		switch (t) {
		case UDPTunnel:
			processVoicePacket(buffer);
			break;
		case Ping:
			// ignore
			break;
		case CodecVersion:
			final boolean oldCanSpeak = canSpeak;
			final CodecVersion codecVersion = CodecVersion.parseFrom(buffer);
			codec = CODEC_NOCODEC;
			if (codecVersion.hasAlpha() &&
					codecVersion.getAlpha() == supportedCodec) {
				codec = CODEC_ALPHA;
			} else if (codecVersion.hasBeta() &&
					codecVersion.getBeta() == supportedCodec) {
				codec = CODEC_BETA;
			}
			canSpeak = canSpeak && (codec != CODEC_NOCODEC);

			if (canSpeak != oldCanSpeak) {
				connectionHost.currentUserUpdated();
			}

			break;
		case Reject:
			final Reject reject = Reject.parseFrom(buffer);
			errorString = String.format(
				"Connection rejected: %s",
				reject.getReason());
			break;
		case ServerSync:
			final ServerSync ss = ServerSync.parseFrom(buffer);

			// On the off chance that there's something in the protocol that
			// results in the current user being changed we'll make sure we
			// don't have two users marked as being the current user.
			if (currentUser != null) {
				currentUser.isCurrent = false;
			}

			currentUser = findUser(ss.getSession());
			currentUser.isCurrent = true;
			currentChannel = currentUser.getChannel();

			pingThread = new Thread(new PingThread(this), "ping");
			pingThread.start();
			Log.i(Globals.LOG_TAG, ">>> " + t);

			ao = new AudioOutput(audioHost);
			audioOutputThread = new Thread(ao, "audio output");
			audioOutputThread.start();

			final UserState.Builder usb = UserState.newBuilder();
			usb.setSession(currentUser.session);
//			usb.setPluginContext(ByteString
//					.copyFromUtf8("Manual placement\000test"));
			sendMessage(MessageType.UserState, usb);

			connectionHost.currentChannelChanged();
			connectionHost.currentUserUpdated();
			break;
		case ChannelState:
			final ChannelState cs = ChannelState.parseFrom(buffer);
			channel = findChannel(cs.getChannelId());
			if (channel != null) {
				if (cs.hasName()) {
					channel.name = cs.getName();
				}
				connectionHost.channelUpdated(channel);
				break;
			}

			// New channel
			channel = new Channel();
			channel.id = cs.getChannelId();
			channel.name = cs.getName();
			channels.put(channel.id, channel);
			connectionHost.channelAdded(channel);
			break;
		case ChannelRemove:
			final ChannelRemove cr = ChannelRemove.parseFrom(buffer);
			channel = findChannel(cr.getChannelId());
			channel.removed = true;
			channels.remove(channel.id);
			connectionHost.channelRemoved(channel.id);
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

			if (us.hasMute()) {
				user.muted = us.getMute();
			}

			if (us.hasDeaf()) {
				user.deafened = us.getDeaf();
				user.muted |= user.deafened;
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
				connectionHost.channelUpdated(user.getChannel());
			}

			if (added) {
				connectionHost.userAdded(user);
			} else {
				connectionHost.userUpdated(user);
			}

			if (currentUserUpdated) {
				connectionHost.currentUserUpdated();
			}
			if (currentUserUpdated && channelUpdated) {
				currentChannel = user.getChannel();
				connectionHost.currentChannelChanged();
			}
			break;
		case UserRemove:
			final UserRemove ur = UserRemove.parseFrom(buffer);
			user = findUser(ur.getSession());
			users.remove(user.session);

			// Remove the user from the channel as well.
			user.getChannel().userCount--;

			connectionHost.channelUpdated(user.getChannel());
			connectionHost.userRemoved(user.session);
			break;
		case TextMessage:
			handleTextMessage(TextMessage.parseFrom(buffer));
			break;
		case CryptSetup:
			final CryptSetup cryptsetup = CryptSetup.parseFrom(buffer);

			if (cryptsetup.hasKey() && cryptsetup.hasClientNonce() &&
				cryptsetup.hasServerNonce()) {
				cryptState.SetKeys(
					cryptsetup.getKey().toByteArray(),
					cryptsetup.getClientNonce().toByteArray(),
					cryptsetup.getServerNonce().toByteArray());
			}
			break;
		default:
			Log.i(Globals.LOG_TAG, "unhandled message type " + t);
		}
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
			Log.e(Globals.LOG_TAG, "User session " + uiSession + "not found!");
		}

		// Rewind the packet. Otherwise consumers are confusing to implement.
		pds.rewind();
		ao.addFrameToBuffer(u, pds, flags);
	}
}
