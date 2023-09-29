package univ.nc.fx.network.tcp.tchat.client;

import univ.nc.fx.network.tcp.tchat.ITchat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

/**
 * Client de tchat
 */
public class Client extends Thread implements ITchat {

	public ClientUI clientUI;
	public String hostname;
	public SocketChannel clientSocketChannel;
	public String nom;
	public int port;
	public Selector selector;
	public Charset charset = Charset.forName("UTF-8");

	/**
	 * Constructeur de la classe client
	 *
	 * @param clientUI références a la classe ClientUI.
	 * @param hostname adresse ip du client.
	 * @param nom      nom du client.
	 * @param port     port du client.
	 */
	public Client(ClientUI clientUI, String hostname, String nom, int port) {
		this.clientUI = clientUI;
		this.nom = nom;
		this.port = port;
		this.hostname = hostname;

		try {
			selector = Selector.open();
			this.clientSocketChannel = SocketChannel.open();
			this.clientSocketChannel.connect(new InetSocketAddress(hostname, port));
			this.clientSocketChannel.configureBlocking(false);
			this.clientSocketChannel.register(selector, SelectionKey.OP_CONNECT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Envoyer un message au serveur.
	 *
	 * @param message Le message à envoyer.
	 */
	public void message(String message) {
		try {
			byte[] result = new String(message).getBytes();
			ByteBuffer buffer = ByteBuffer.wrap(result);
			buffer.clear();
			clientSocketChannel.register(selector, SelectionKey.OP_WRITE);
			buffer.put(message.getBytes());
			buffer.flip();
			clientSocketChannel.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Lire la clé de sélection (SelectionKey) pour les opérations de lecture.
	 *
	 * @param key La clé de sélection (SelectionKey) à traiter.
	 */
	public void readKey(SelectionKey key) {
		if (key.isReadable()) {
			SocketChannel clientSocketChannel = (SocketChannel) key.channel();
			ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);

			String message = "";
			try {
				while (clientSocketChannel.read(buffer) > 0) {
					buffer.flip();
					message = message + charset.decode(buffer);
				}
				clientUI.appendMessage(message + "\n");

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Méthode principale d'exécution du client.
	 * Surveille en permanence les événements de lecture et traite les messages
	 * entrants.
	 */
	public void run() {
		try {
			while (clientUI.isRunning()) {
				selector.select();
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				Iterator<SelectionKey> keys_Ite = selectedKeys.iterator();

				while (keys_Ite.hasNext()) {
					SelectionKey selectKey = (SelectionKey) keys_Ite.next();
					selectKey.interestOps(SelectionKey.OP_READ);
					keys_Ite.remove();
					readKey(selectKey);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
