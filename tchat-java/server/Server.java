package univ.nc.fx.network.tcp.tchat.server;

import javafx.application.Platform;
import univ.nc.fx.network.tcp.tchat.ITchat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Processus serveur qui ecoute les connexions entrantes,
 * les messages entrants, et les rediffuse aux clients connectes.
 */
public class Server extends Thread implements ITchat {

    public ServerSocketChannel serverChannel;
    public Selector selector;
    public ServerUI ui;
    public String ip;
    public int port;
    public Charset charset = Charset.forName("UTF-8");

    /**
     * Constructeur du serveur de chat.
     *
     * @param ui   référence a la classe ServerUI.
     * @param ip   Adresse IP sur laquelle écouter les connexions.
     * @param port Port sur lequel écouter les connexions entrantes.
     */
    public Server(ServerUI ui, String ip, int port) {
        this.ui = ui;
        this.ip = ip;
        this.port = port;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            InetSocketAddress address = new InetSocketAddress(ip, port);
            serverChannel.socket().bind(address);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            sendLogToUI("le serveur a demarrer sur " + ip + ":" + port);
        } catch (IOException e) {
            System.out.println("Le serveur n'a pas pu demarrer");
            e.printStackTrace();
        }
    }

    /**
     * Accepter un client entrant ou lire un message d'un client existant.
     *
     * @param ssc       ServerSocketChannel pour accepter de nouveaux clients.
     * @param selectKey Clé de sélection associée à l'événement.
     */
    public void acceptClient(ServerSocketChannel ssc, SelectionKey selectKey) throws IOException {
        if (selectKey.isAcceptable()) {
            acceptNouveauClient(ssc);
        }
        if (selectKey.isReadable()) {
            readMessage(selectKey);
        }
    }

    /**
     * Accepter un nouveau client entrant.
     *
     * @param ssc ServerSocketChannel pour accepter de nouveaux clients.
     */
    public void acceptNouveauClient(ServerSocketChannel ssc) {
        try {
            SocketChannel client = ssc.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);

            System.out.println("Nouveau client connecté depuis : " + client.getRemoteAddress());

            sendLogToUI("Client connecté : " + client.getRemoteAddress());

            afficherClientsConnectes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lire un message du client.
     *
     * @param selectKey Clé de sélection associée à l'événement de lecture.
     */
    public void readMessage(SelectionKey selectKey) throws IOException {
        SocketChannel client = (SocketChannel) selectKey.channel();
        ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);
        StringBuilder message = new StringBuilder();
        try {
            while (client.read(buffer) > 0) {
                buffer.flip();
                message.append(charset.decode(buffer).toString());
            }
            selectKey.interestOps(SelectionKey.OP_READ);
            sendLogToUI(message.toString());
        } catch (IOException e) {
            selectKey.cancel();
            selectKey.channel().close();
        }
        if (message.length() > 0) {
            sendAllToClient(message.toString());
        }
    }

    /**
     * Afficher la liste des clients connectés.
     *
     */
    public void afficherClientsConnectes() throws IOException {
        System.out.println("Clients connectés :");
        for (SelectionKey selectKey : selector.keys()) {
            Channel channel = selectKey.channel();
            if (channel != null && channel instanceof SocketChannel) {
                SocketChannel clientChannel = (SocketChannel) channel;
                System.out.println("Client : " + clientChannel.getRemoteAddress());
            }
        }
    }

    /**
     * Envoyer un message à tous les clients connectés.
     *
     * @param message Le message à envoyer à tous les clients.
     */
    public void sendAllToClient(String message) {
        for (SelectionKey selectKey : selector.keys()) {
            Channel channel = selectKey.channel();
            if (channel != null && channel instanceof SocketChannel) {
                SocketChannel clientChannel = (SocketChannel) channel;
                try {
                    clientChannel.write(charset.encode(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Envoyer un message de journal à l'interface utilisateur.
     *
     * @param message Le message de journal à afficher dans l'interface utilisateur.
     */
    public void sendLogToUI(String message) {
        Platform.runLater(() -> ui.log(message));
    }

    /**
     * Méthode principale d'exécution du serveur.
     * Surveille en permanence les événements de sélection et traite les clients.
     */
    public void run() {
        try {
            while (ui.isRunning()) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> selectedKeysIterator = selectedKeys.iterator();

                while (selectedKeysIterator.hasNext()) {
                    SelectionKey selectKey = (SelectionKey) selectedKeysIterator.next();
                    selectedKeysIterator.remove();
                    acceptClient(serverChannel, selectKey);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
