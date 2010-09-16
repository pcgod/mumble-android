package org.pcgod.mumbleclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;

import javax.crypto.Cipher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import net.sf.mumble.MumbleProto.Authenticate;
import net.sf.mumble.MumbleProto.ChannelRemove;
import net.sf.mumble.MumbleProto.ChannelState;
import net.sf.mumble.MumbleProto.ServerSync;
import net.sf.mumble.MumbleProto.TextMessage;
import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;
import net.sf.mumble.MumbleProto.Version;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public class MumbleClient implements Runnable {
	public enum MessageType {
		Version, UDPTunnel, Authenticate, Ping, Reject, ServerSync, ChannelRemove, ChannelState, UserRemove, UserState, BanList, TextMessage, PermissionDenied, ACL, QueryUsers, CryptSetup, ContextActionAdd, ContextAction, UserList, VoiceTarget, PermissionQuery, CodecVersion, UserStats, RequestBlob, ServerConfig
	}

//	public static final int MESSAGETYPE_VERSION = 0;
//	public static final int MESSAGETYPE_UDPTUNNEL = 1;
//	public static final int MESSAGETYPE_AUTHENTICATE = 2;
//	public static final int MESSAGETYPE_PING = 3;
//	public static final int MESSAGETYPE_REJECT = 4;
//	public static final int MESSAGETYPE_SERVERSYNC = 5;
//	public static final int MESSAGETYPE_CHANNELREMOVE = 6;
//	public static final int MESSAGETYPE_CHANNELSTATE = 7;
//	public static final int MESSAGETYPE_USERREMOVE = 8;
//	public static final int MESSAGETYPE_USERSTATE = 9;
//	public static final int MESSAGETYPE_BANLIST = 10;
//	public static final int MESSAGETYPE_TEXTMESSAGE = 11;
//	public static final int MESSAGETYPE_PERMISSIONDENIED = 12;
//	public static final int MESSAGETYPE_ACL = 13;
//	public static final int MESSAGETYPE_QUERYUSERS = 14;
//	public static final int MESSAGETYPE_CRYPTSETUP = 15;
//	public static final int MESSAGETYPE_CONTEXTACTIONADD = 16;
//	public static final int MESSAGETYPE_CONTEXTACTION = 17;
//	public static final int MESSAGETYPE_USERLIST = 18;
//	public static final int MESSAGETYPE_VOICETARGET = 19;
//	public static final int MESSAGETYPE_PERMISSIONQUERY = 20;
//	public static final int MESSAGETYPE_CODECVERSION = 21;
//	public static final int MESSAGETYPE_USERSTATS = 22;
//	public static final int MESSAGETYPE_REQUESTBLOB = 23;
//	public static final int MESSAGETYPE_SERVERCONFIG = 24;

	public static final int UDPMESSAGETYPE_UDPVOICECELTALPHA = 0;
	public static final int UDPMESSAGETYPE_UDPPING = 1;
	public static final int UDPMESSAGETYPE_UDPVOICESPEEX = 2;
	public static final int UDPMESSAGETYPE_UDPVOICECELTBETA = 3;

	public static final int SAMPLE_RATE = 48000;
	public static final int FRAME_SIZE = SAMPLE_RATE / 100;
	public static final String INTENT_CHANNEL_LIST_UPDATE = "mumbleclient.intent.CHANNEL_LIST_UPDATE";
	public static final String INTENT_CURRENT_CHANNEL_CHANGED = "mumbleclient.intent.CURRENT_CHANNEL_CHANGED";
	public static final String INTENT_USER_LIST_UPDATE = "mumbleclient.intent.USER_LIST_UPDATE";
	public static final String INTENT_CHAT_TEXT_UPDATE = "mumbleclient.intent.CHAT_TEXT_UPDATE";
	private static final String LOG_TAG = "mumbleclient";
	private static final MessageType[] MT_CONSTANTS = MessageType.class.getEnumConstants();

	private static final int protocolVersion = (1 << 16) | (2 << 8)
			| (3 & 0xFF);

	public ArrayList<Channel> channelArray = new ArrayList<Channel>();
	public int currentChannel = -1;
	public int session;
	public boolean canSpeak = true;
	public ArrayList<User> userArray = new ArrayList<User>();
	private boolean authenticated;
	private final Context ctx;
	public LinkedList<String> chatList = new LinkedList<String>();

	private DataInputStream in;
	private DataOutputStream out;
	private Thread pingThread;
	private Socket socket;

	private final String host;
	private final int port;
	private final String username;
	private final String password;

	private Cipher encryptCipher;
	private Cipher decryptCipher;
	private AudioOutput ao;
	private Thread audioOutputThread;

	public MumbleClient(final Context ctx_, final String host_,
			final int port_, final String username_, final String password_) {
		ctx = ctx_.getApplicationContext();
		host = host_;
		port = port_;
		username = username_;
		password = password_;
	}

	public final boolean isConnected() {
		return socket != null && socket.isConnected();
	}

	public final boolean isSameServer(final String host_, final int port_,
			final String username_, final String password_) {
		return host.equals(host_) && port == port_
				&& username.equals(username_) && password.equals(password_);
	}

	public final void joinChannel(final int channelId) {
		final UserState.Builder us = UserState.newBuilder();
		us.setSession(session);
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
			final SSLContext ctx_ = SSLContext.getInstance("TLS");
			ctx_.init(null, new TrustManager[] { new LocalSSLTrustManager() },
					null);
			final SSLSocketFactory factory = ctx_.getSocketFactory();
			final SSLSocket socket_ = (SSLSocket) factory.createSocket(host,
					port);
			socket_.setUseClientMode(true);
			socket_.setEnabledProtocols(new String[] { "TLSv1" });
			socket_.startHandshake();

			handleProtocol(socket_);
		} catch (final NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (final KeyManagementException e) {
			e.printStackTrace();
		} catch (final UnknownHostException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public final void sendChannelTextMessage(final String message) {
		final TextMessage.Builder tmb = TextMessage.newBuilder();
		tmb.addChannelId(currentChannel);
		tmb.setMessage(message);
		try {
			sendMessage(MessageType.TextMessage, tmb);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		final StringBuffer mb = new StringBuffer();
		mb.append("[");
		mb.append(DateUtils.formatDateTime(ctx, System.currentTimeMillis(),
				DateUtils.FORMAT_SHOW_TIME));
		mb.append("] To ");
		final Channel c = findChannel(currentChannel);
		mb.append(c.name);
		mb.append(": ");
		mb.append(message);
		mb.append("\n");
		chatList.add(mb.toString());
		sendBroadcast(INTENT_CHAT_TEXT_UPDATE);
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
			Log.i(LOG_TAG, "<<< " + t);
		}
	}

	public final void sendUdpTunnelMessage(final byte[] buffer)
			throws IOException {
		final short type = (short) MessageType.UDPTunnel.ordinal();
		final int length = buffer.length;

		synchronized (out) {
			out.writeShort(type);
			out.writeInt(length);
			out.write(buffer);
		}
	}

	private Channel findChannel(final int id) {
		for (final Channel c : channelArray) {
			if (c.id == id) {
				return c;
			}
		}

		return null;
	}

	private User findUser(final int session_) {
		for (final User u : userArray) {
			if (u.session == session_) {
				return u;
			}
		}

		return null;
	}

	private void handleProtocol(final Socket socket_) throws IOException {
		socket = socket_;
		out = new DataOutputStream(socket_.getOutputStream());
		in = new DataInputStream(socket_.getInputStream());

		final Version.Builder v = Version.newBuilder();
		v.setVersion(protocolVersion);
		v.setRelease("javalib 0.0.1-dev");

		final Authenticate.Builder a = Authenticate.newBuilder();
		a.setUsername(username);
		a.setPassword(password);
		a.addCeltVersions(0x8000000b);

		sendMessage(MessageType.Version, v);
		sendMessage(MessageType.Authenticate, a);

		byte[] msg = null;
		while (socket_.isConnected()) {
			final short type = in.readShort();
			final int length = in.readInt();
			if (msg == null || msg.length != length) {
				msg = new byte[length];
			}
			in.readFully(msg);
			processMsg(MT_CONSTANTS[type], msg);
		}
	}

	private void handleTextMessage(final TextMessage ts) {
		User u = null;
		if (ts.hasActor()) {
			u = findUser(ts.getActor());
		}
		final StringBuffer message = new StringBuffer();
		message.append("[");
		message.append(DateUtils.formatDateTime(ctx,
				System.currentTimeMillis(), DateUtils.FORMAT_SHOW_TIME));
		message.append("] ");
		if (ts.getChannelIdCount() > 0) {
			message.append("(C) ");
		}
		if (ts.getTreeIdCount() > 0) {
			message.append("(T) ");
		}
		if (u != null) {
			message.append(u.name);
		} else {
			message.append("Server");
		}
		message.append(": ");
		message.append(ts.getMessage());
		message.append("\n");
		chatList.add(message.toString());
		sendBroadcast(INTENT_CHAT_TEXT_UPDATE);
	}

	@SuppressWarnings("unused")
	private void printChanneList() {
		Log.i(LOG_TAG, "--- begin channel list ---");
		for (final Channel c : channelArray) {
			Log.i(LOG_TAG, c.toString());
		}
		Log.i(LOG_TAG, "--- end channel list ---");
	}

	@SuppressWarnings("unused")
	private void printUserList() {
		Log.i(LOG_TAG, "--- begin user list ---");
		for (final User u : userArray) {
			Log.i(LOG_TAG, u.toString());
		}
		Log.i(LOG_TAG, "--- end user list ---");
	}

	private void processMsg(final MessageType t, final byte[] buffer)
			throws IOException {
		switch (t) {
		case UDPTunnel:
			processVoicePacket(buffer);
			break;
		case Ping:
			// ignore
			break;
		case ServerSync:
			final ServerSync ss = ServerSync.parseFrom(buffer);
			session = ss.getSession();
			authenticated = true;

			final User user = findUser(session);
			currentChannel = user.channel;

			pingThread = new Thread(new PingThread(this), "ping");
			pingThread.start();
			Log.i(LOG_TAG, ">>> " + t);

			ao = new AudioOutput();
			audioOutputThread = new Thread(ao, "audio output");
			audioOutputThread.start();

			final UserState.Builder usb = UserState.newBuilder();
			usb.setSession(session);
//			usb.setPluginContext(ByteString
//					.copyFromUtf8("Manual placement\000test"));
			sendMessage(MessageType.UserState, usb);

			sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
			break;
		case ChannelState:
			final ChannelState cs = ChannelState.parseFrom(buffer);
			Channel c = findChannel(cs.getChannelId());
			if (c != null) {
				if (cs.hasName()) {
					c.name = cs.getName();
				}
				sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
				break;
			}
			// New channel
			c = new Channel();
			c.id = cs.getChannelId();
			c.name = cs.getName();
			channelArray.add(c);
			sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
			break;
		case ChannelRemove:
			final ChannelRemove cr = ChannelRemove.parseFrom(buffer);
			channelArray.remove(findChannel(cr.getChannelId()));

			sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
			break;
		case UserState:
			final UserState us = UserState.parseFrom(buffer);
			User u = findUser(us.getSession());
			if (u != null) {
				if (us.hasChannelId()) {
					u.channel = us.getChannelId();
					if (us.getSession() == session) {
						currentChannel = u.channel;
						sendBroadcast(INTENT_CURRENT_CHANNEL_CHANGED);
					}
					sendBroadcast(INTENT_USER_LIST_UPDATE);
				}
				if (us.getSession() == session) {
					if (us.hasMute() || us.hasSuppress()) {
						if (us.hasMute()) {
							canSpeak = !us.getMute();
						}
						if (us.hasSuppress()) {
							canSpeak = !us.getSuppress();
						}
						sendBroadcast(INTENT_USER_LIST_UPDATE);
					}
				}
				break;
			}
			// New user
			u = new User();
			u.session = us.getSession();
			u.name = us.getName();
			u.channel = us.getChannelId();
			userArray.add(u);

			sendBroadcast(INTENT_USER_LIST_UPDATE);
			break;
		case UserRemove:
			final UserRemove ur = UserRemove.parseFrom(buffer);
			userArray.remove(findUser(ur.getSession()));

			sendBroadcast(INTENT_USER_LIST_UPDATE);
			break;
		case TextMessage:
			handleTextMessage(TextMessage.parseFrom(buffer));
			break;
		case CryptSetup:
//			try {
//				final CryptSetup cryptSetup = CryptSetup.parseFrom(buffer);
//				encryptCipher = Cipher.getInstance("AES/OCB/NoPadding");
//				decryptCipher = Cipher.getInstance("AES/OCB/NoPadding");
//				SecretKeySpec cryptKey = new SecretKeySpec(cryptSetup.getKey().toByteArray(), "AES");
//				IvParameterSpec encryptIv = new IvParameterSpec(cryptSetup.getServerNonce().toByteArray());
//				IvParameterSpec decryptIv = new IvParameterSpec(cryptSetup.getClientNonce().toByteArray());
//				encryptCipher.init(Cipher.ENCRYPT_MODE, cryptKey, encryptIv);
//				decryptCipher.init(Cipher.DECRYPT_MODE, cryptKey, decryptIv);
//			} catch (NoSuchAlgorithmException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchPaddingException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (InvalidKeyException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (InvalidAlgorithmParameterException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			break;
		default:
			Log.i(LOG_TAG, "unhandled message type " + t);
		}
	}

	private void processVoicePacket(final byte[] buffer) {
		final int type = buffer[0] >> 5 & 0x7;
		final int flags = buffer[0] & 0x1f;

		// There is no speex support...
		if (type != UDPMESSAGETYPE_UDPVOICECELTALPHA && type != UDPMESSAGETYPE_UDPVOICECELTBETA) {
			return;
		}

		final PacketDataStream pds = new PacketDataStream(buffer);
		// skip type / flags
		pds.skip(1);
		final long uiSession = pds.readLong();

		final User u = findUser((int) uiSession);
		if (u == null) {
			Log.e(LOG_TAG, "User session " + uiSession + "not found!");
		}

		ao.addFrameToBuffer(u, pds, flags);
	}

	private void recountChannelUsers() {
		for (final Channel c : channelArray) {
			c.userCount = 0;
		}

		for (final User u : userArray) {
			final Channel c = findChannel(u.channel);
			c.userCount++;
		}
	}

	private void sendBroadcast(final String action) {
		if (!authenticated) {
			return;
		}
		recountChannelUsers();
		final Intent i = new Intent(action);
		ctx.sendBroadcast(i);
	}
}
