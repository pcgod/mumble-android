package org.pcgod.mumbleclient.service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import junit.framework.Assert;
import net.sf.mumble.MumbleProto.Authenticate;
import net.sf.mumble.MumbleProto.Version;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.service.MumbleProtocol.MessageType;

import android.os.Environment;
import android.util.Log;

import com.google.protobuf.MessageLite;

/**
 * Maintains connection to the server and implements the low level communication
 * protocol.
 *
 * This class should support calls from both the main application thread and its
 * own connection thread. As a result the disconnecting state is quite
 * complicated. Disconnect can basically happen at any point as the server might
 * cut the connection due to restart or kick.
 *
 * When disconnection happens, the connection reports "Disconnected" state and
 * stops handling incoming or outgoing messages. Since at this point some of
 * the sockets might already be closed there is no use waiting on Disconnected
 * reporting until all the other threads, such as PingThread or RecordThread
 * have been stopped.
 *
 * @author pcgod
 */
public class MumbleConnection implements Runnable {
	/**
	 * Socket reader for the TCP socket. Interprets the Mumble TCP envelope and
	 * extracts the data inside.
	 *
	 * @author Rantanen
	 *
	 */
	class TcpSocketReader extends MumbleSocketReader {
		private byte[] msg = null;

		public TcpSocketReader(final Object monitor) {
			super(monitor, "TcpReader");
		}

		@Override
		public boolean isRunning() {
			return !disconnecting && super.isRunning();
		}

		@Override
		public void stop() {
			try {
				tcpSocket.close();
			} catch (final IOException e) {
				Globals.logError(this, "Error when closing tcp socket", e);
			}
			super.stop();
		}

		@Override
		protected void process() throws IOException {
			final short type = in.readShort();
			final int length = in.readInt();
			if (msg == null || msg.length != length) {
				msg = new byte[length];
			}
			in.readFully(msg);

			protocol.processTcp(type, msg);
		}
	};

	/**
	 * Socket reader for the UDP socket. Decrypts the data from the raw UDP
	 * packages.
	 *
	 * @author Rantanen
	 *
	 */
	class UdpSocketReader extends MumbleSocketReader {
		private final DatagramPacket packet = new DatagramPacket(
			new byte[UDP_BUFFER_SIZE],
			UDP_BUFFER_SIZE);

		public UdpSocketReader(final Object monitor) {
			super(monitor, "UdpReader");
		}

		@Override
		public boolean isRunning() {
			return !disconnecting && super.isRunning();
		}

		@Override
		public void stop() {
			udpSocket.close();
			super.stop();
		}

		@Override
		protected void process() throws IOException {
			udpSocket.receive(packet);

			final byte[] buffer = cryptState.decrypt(
				packet.getData(),
				packet.getLength());

			// Decrypt might return null if the buffer was total garbage.
			if (buffer == null) {
				return;
			}

			protocol.processUdp(buffer, buffer.length);
		}
	};

	public static final int UDP_BUFFER_SIZE = 2048;

	private final MumbleConnectionHost connectionHost;
	private MumbleProtocol protocol;

	private Socket tcpSocket;
	private DataInputStream in;
	private DataOutputStream out;
	private DatagramSocket udpSocket;
	private long useUdpUntil;
	boolean usingUdp = false;

	/**
	 * Signals disconnecting state. True if something has interrupted the normal
	 * operation of the MumbleConnection thread and it should stop.
	 */
	private volatile boolean disconnecting = false;

	/**
	 * Signals whether connection terminating errors should be suspected. Mainly
	 * used to suppress some IO/Interruption errors that occur as a result of
	 * stopping the MumbleConnection after calling disconnect()
	 */
	private volatile boolean suppressErrors = false;

	private InetAddress hostAddress;
	private final String host;
	private final int port;
	private final String username;
	private final String password;
	private final String keystorePassword;
	private final File keystoreFile;
	private final boolean useKeystore;

	private final Object stateLock = new Object();
	final CryptState cryptState = new CryptState();

	/**
	 * Constructor for new connection thread.
	 *
	 * This thread should be started shortly after construction. Construction
	 * sets the connection state for the host to "Connecting" even if the actual
	 * connection won't be attempted until the thread has been started.
	 *
	 * This is to combat an issue where the Service is asked to connect and the
	 * thread is started but the thread isn't given execution time before
	 * another activity checks for connection state and finds out the service is
	 * in Disconnected state.
	 *
	 * @param connectionHost
	 *            Host interface for this Connection
	 * @param host
	 *            Mumble server host address
	 * @param port
	 *            Mumble server port
	 * @param username
	 *            Username
	 * @param password
	 *            Server password
	 */
	public MumbleConnection(
		final MumbleConnectionHost connectionHost,
		final String host,
		final int port,
		final String username,
		final String password,
		final String keystoreFile,
		final String keystorePassword) {
		this.connectionHost = connectionHost;
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
		this.keystorePassword = keystorePassword;
		if (keystoreFile != null && !keystoreFile.equals("")) {
			this.keystoreFile = new File(Environment.getExternalStorageDirectory(), keystoreFile);
			Globals.logInfo(this, "MumbleConnection: Using keystore file: " + this.keystoreFile.getAbsolutePath());
			this.useKeystore = true;
		} else {
			this.useKeystore = false;
			this.keystoreFile = null;
		}
		connectionHost.setConnectionState(MumbleConnectionHost.STATE_CONNECTING);
	}

	public final void disconnect() {
		synchronized (stateLock) {
			if (disconnecting == true) {
				return;
			}

			Globals.logInfo(this, "disconnect()");
			disconnecting = true;
			suppressErrors = true;

			// Close sockets to interrupt the reader threads. We don't need to
			// be completely certain that they won't be re-opened by another
			// thread as the connection thread will close them anyway. This is
			// just to interrupt the reader threads.
			try {
				if (tcpSocket != null) {
					tcpSocket.close();
				}
			} catch (final IOException e) {
				Globals.logError(this, "Error disconnecting TCP socket", e);
			}
			if (udpSocket != null) {
				udpSocket.close();
			}

			connectionHost.setConnectionState(MumbleConnectionHost.STATE_DISCONNECTED);
			stateLock.notifyAll();
		}
	}

	public final boolean isConnectionAlive() {
		return !disconnecting && udpSocket != null && tcpSocket != null &&
			   !tcpSocket.isClosed() && tcpSocket.isConnected() &&
			   !udpSocket.isClosed();
	}

	public final boolean isSameServer(
		final String host_,
		final int port_,
		final String username_,
		final String password_) {
		return host.equals(host_) && port == port_ &&
			   username.equals(username_) && password.equals(password_);
	}

	public void refreshUdpLimit(final long limit) {
		useUdpUntil = limit;
	}

	@Override
	public final void run() {
		Assert.assertNotNull(protocol);

		boolean connected = false;
		try {
			try {
				Globals.logInfo(this, String.format(
					"Connecting to host \"%s\", port %s",
					host,
					port));

				this.hostAddress = InetAddress.getByName(host);
				tcpSocket = connectTcp();
				udpSocket = connectUdp();
				connected = true;
			} catch (final UnknownHostException e) {
				final String errorString = String.format(
					"Host \"%s\" unknown",
					host);
				reportError(errorString, e);
			} catch (final ConnectException e) {
				final String errorString = "The host refused connection";
				reportError(errorString, e);
			} catch (final KeyManagementException e) {
				reportError(String.format(
					"Could not connect to Mumble server \"%s:%s\"",
					host,
					port), e);
			} catch (final NoSuchAlgorithmException e) {
				reportError(String.format(
					"Could not connect to Mumble server \"%s:%s\"",
					host,
					port), e);
			} catch (final IOException e) {
				reportError(String.format(
					"Could not connect to Mumble server \"%s:%s\"",
					host,
					port), e);
			} catch (final GeneralSecurityException e) {
				reportError(String.format(
					"Could not connect to Mumble server \"%s:%s\"",
					host,
					port), e);
			}

			// If we couldn't finish connecting, return.
			if (!connected) {
				return;
			}

			synchronized (stateLock) {
				if (disconnecting) {
					return;
				}

				connectionHost.setConnectionState(MumbleConnectionHost.STATE_CONNECTED);
			}

			try {
				handleProtocol();
			} catch (final IOException e) {
				final String errorString = String.format(
					"Connection lost",
					host);
				reportError(errorString, e);
			} catch (final InterruptedException e) {
				final String errorString = String.format(
					"Connection lost",
					host);
				reportError(errorString, e);
			}
		} finally {
			synchronized (stateLock) {
				// Don't re-update state in case we are already disconnecting.
				// ANOTHER MumbleConnection thread might have already set state
				// to connected and this confuses everything.
				if (!disconnecting) {
					disconnecting = true;
					connectionHost.setConnectionState(MumbleConnectionHost.STATE_DISCONNECTED);
				}
			}

			cleanConnection();
		}
	}

	/**
	 * Sends TCP message. As it is impossible to predict the socket state this
	 * method must be exception safe. If the sockets have gone stale it reports
	 * error and initiates connection shutdown.
	 *
	 * @param t
	 *            Message type
	 * @param b
	 *            Protocol Buffer message builder
	 */
	public final void sendTcpMessage(
		final MessageType t,
		final MessageLite.Builder b) {
		final MessageLite m = b.build();
		final short type = (short) t.ordinal();
		final int length = m.getSerializedSize();

		if (disconnecting) {
			return;
		}

		try {
			synchronized (out) {
				out.writeShort(type);
				out.writeInt(length);
				m.writeTo(out);
			}
		} catch (final IOException e) {
			handleSendingException(e);
		}

		if (t != MessageType.Ping) {
			Globals.logDebug(this, "<<< " + t);
		}
	}

	/**
	 * Sends UDP message. See sendTcpMessage for additional information
	 * concerning exceptions.
	 *
	 * @param buffer
	 *            Udp message buffer
	 * @param length
	 *            Message length
	 * @param forceUdp
	 *            True if the message should never be tunneled through TCP
	 */
	public final void sendUdpMessage(
		final byte[] buffer,
		final int length,
		final boolean forceUdp) {
		// FIXME: This would break things because we don't handle nonce resync messages
//		if (!cryptState.isInitialized()) {
//			return;
//		}

		if (forceUdp || useUdpUntil > System.currentTimeMillis()) {
			if (!usingUdp && !forceUdp) {
				Globals.logInfo(this, "UDP enabled");
				usingUdp = true;
			}

			final byte[] encryptedBuffer = cryptState.encrypt(buffer, length);
			final DatagramPacket outPacket = new DatagramPacket(
				encryptedBuffer,
				encryptedBuffer.length);

			outPacket.setAddress(hostAddress);
			outPacket.setPort(port);

			if (disconnecting) {
				return;
			}

			try {
				udpSocket.send(outPacket);
			} catch (final IOException e) {
				handleSendingException(e);
			}
		} else {
			if (usingUdp) {
				Globals.logInfo(this, "UDP disabled");
				usingUdp = false;
			}

			final short type = (short) MessageType.UDPTunnel.ordinal();

			if (disconnecting) {
				return;
			}

			synchronized (out) {
				try {
					out.writeShort(type);
					out.writeInt(length);
					out.write(buffer, 0, length);
				} catch (final IOException e) {
					handleSendingException(e);
				}
			}
		}
	}

	public Thread start(final MumbleProtocol protocol_) {
		this.protocol = protocol_;

		final Thread t = new Thread(this, "MumbleConnection");
		t.start();
		return t;
	}

	private void cleanConnection() {
		// FIXME: These throw exceptions for some reason.
		// Even with the checks in place
		if (tcpSocket != null && tcpSocket.isConnected()) {
			try {
				tcpSocket.close();
			} catch (final IOException e) {
				Globals.logError(this,
					"IO error while closing the tcp socket",
					e);
			}
		}
		if (udpSocket != null && udpSocket.isConnected()) {
			udpSocket.close();
		}
	}

	private void handleProtocol() throws IOException, InterruptedException {
		if (disconnecting) {
			return;
		}

		out = new DataOutputStream(tcpSocket.getOutputStream());
		in = new DataInputStream(tcpSocket.getInputStream());

		final Version.Builder v = Version.newBuilder();
		v.setVersion(Globals.PROTOCOL_VERSION);
		v.setRelease("MumbleAndroid 0.0.1-dev");

		final Authenticate.Builder a = Authenticate.newBuilder();
		a.setUsername(username);
		a.setPassword(password);
		a.addCeltVersions(Globals.CELT_VERSION);

		sendTcpMessage(MessageType.Version, v);
		sendTcpMessage(MessageType.Authenticate, a);

		if (disconnecting) {
			return;
		}

		// Spawn one thread for each socket to allow concurrent processing.
		final MumbleSocketReader tcpReader = new TcpSocketReader(stateLock);
		final MumbleSocketReader udpReader = new UdpSocketReader(stateLock);

		tcpReader.start();
		udpReader.start();

		synchronized (stateLock) {
			while (!disconnecting &&
				   tcpReader.isRunning() && udpReader.isRunning()) {
				stateLock.wait();
			}

			// Report error if we died without being in a disconnecting state.
			if (!disconnecting) {
				reportError("Connection lost", null);
			}

			if (!disconnecting) {
				disconnecting = true;
				connectionHost.setConnectionState(MumbleConnectionHost.STATE_DISCONNECTED);
			}
		}

		// Stop readers in case one of them is still running
		tcpReader.stop();
		udpReader.stop();
	}

	private boolean handleSendingException(final IOException e) {
		// If we are already disconnecting, just ignore this.
		if (disconnecting) {
			return true;
		}

		// Otherwise see if we should be disconnecting really.
		if (!isConnectionAlive()) {
			reportError(String.format("Connection lost: %s", e.getMessage()), e);
			disconnect();
		} else {
			// Connection is alive but we still couldn't send message?
			reportError(String.format(
				"Error while sending message: %s",
				e.getMessage()), e);
		}

		return false;
	}

	private void reportError(final String error, final Exception e) {
		if (suppressErrors) {
			Globals.logWarn(this, "Error while disconnecting");
			Globals.logWarn(this, error, e);
			return;
		}
		connectionHost.setError(String.format(error));
		Globals.logError(this, error, e);
	}

	private SSLSocketFactory getSocketFactory() throws KeyStoreException, UnrecoverableKeyException, CertificateException, NoSuchProviderException, IOException, NoSuchAlgorithmException, KeyManagementException {
		final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		if(this.useKeystore) {
			char[] password = null;
			KeyStore keystore = KeyStore.getInstance("BKS");
			InputStream is = new FileInputStream(this.keystoreFile);
			// only use the keystore password if we have it
			if(this.keystorePassword != null && !this.keystorePassword.equals("")) {
				password = this.keystorePassword.toCharArray();
			}
			keystore.load(is, password);
			kmf.init(keystore, password);
		} else {
			kmf.init(null, null);
		}
		final SSLContext ctx_ = SSLContext.getInstance("TLS");
		ctx_.init(kmf.getKeyManagers(), new TrustManager[] { new LocalSSLTrustManager() }, new SecureRandom());
		return ctx_.getSocketFactory();
	}

	protected Socket connectTcp() throws NoSuchAlgorithmException,
		KeyManagementException, IOException, UnknownHostException, KeyStoreException, UnrecoverableKeyException, CertificateException, NoSuchProviderException {
		final SSLSocketFactory factory = getSocketFactory();
		final SSLSocket sslSocket = (SSLSocket) factory.createSocket(hostAddress, port);
		sslSocket.setUseClientMode(true);
		sslSocket.setKeepAlive(true);
		sslSocket.setEnabledProtocols(new String[] { "TLSv1"});
		sslSocket.startHandshake();

		Globals.logInfo(this, "TCP/SSL socket opened");

		return sslSocket;
	}

	protected DatagramSocket connectUdp() throws SocketException,
		UnknownHostException {
		udpSocket = new DatagramSocket();
		udpSocket.connect(hostAddress, port);

		Globals.logInfo(this, "UDP Socket opened");

		return udpSocket;
	}
}
