package nioPackage.NioClient;

import com.google.gson.JsonObject;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class NioClient implements Runnable {

    private static final int BUFFER_SIZE = 1000;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private Selector selector;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private static final String IP = "0.0.0.0"; // Replace with actual server IP
    private static final int PORT = 5000;
    private boolean loginSent = false;
    private boolean loggedIn = false;
    private int retryCounter = 0;
    private final String loginCredential;

    public NioClient(String loginCredential) {
        this.loginCredential = loginCredential;
    }
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                selector = Selector.open();
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(false);
                channel.register(selector, SelectionKey.OP_CONNECT);
                channel.connect(new InetSocketAddress(IP, PORT));
                logger.info("Attempting connection to " + IP + ":" + PORT);

                while (true) {
                    selector.selectNow();
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    boolean errorInSelection = false;

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) continue;

                        try {
                            if (key.isConnectable()) {
                                if (!finishConnection(key)) {
                                    errorInSelection = true;
                                    break;
                                }
                            }

                            if (!loggedIn) {
                                loggedIn = processLogin(key);
                            } else {
                                if (key.isWritable()) {
                                    byte[] data = generateDeviceData("Device4739");
                                    logger.info("Data sending...");
                                    int bytesWritten = write(key, data);
                                    if (bytesWritten == -1) {
                                        errorInSelection = true;
                                        break;
                                    }
                                    // Optionally wait before next data send
                                    Thread.sleep(2000);
                                }

                                if (key.isReadable()) {
                                    byte[] data = read(key);
                                    if (data != null) {
                                        logger.info("Received data: " + new String(data, StandardCharsets.UTF_8));
                                    }
                                }
                            }

                        } catch (Exception e) {
                            logger.error("Exception during selection processing", e);
                            errorInSelection = true;
                            break;
                        }
                    }

                    if (errorInSelection) {
                        logger.info("Error occurred, reconnecting...");
                        break;
                    }

                    Thread.sleep(10);
                }

            } catch (ClosedByInterruptException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Exception in main client loop", e);
            } finally {
                close();
            }

            retryCounter++;
            logger.info("Reconnect attempt #" + retryCounter);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean finishConnection(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            if (channel.finishConnect()) {
                logger.info("Connected to server");
                key.interestOps(SelectionKey.OP_WRITE);
                loginSent = false;
                loggedIn = false;
                return true;
            }
        } catch (ConnectException e) {
            logger.error("Connection refused", e);
        } catch (IOException e) {
            logger.error("IO exception on connect", e);
        }

        key.cancel();
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("Error closing channel after failed connect", e);
        }
        return false;
    }

    private boolean processLogin(SelectionKey key) throws IOException {
        if (!loginSent && key.isWritable()) {
            byte[] loginBytes = getLoginMessage();
            int written = write(key, loginBytes);

            if (written > 0) {
                logger.info("Login message sent (no response expected): " + new String(loginBytes, StandardCharsets.UTF_8));
                loginSent = true;
                key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ); // Start sending data right away
                return true; // Consider login successful
            } else {
                logger.warn("Login write failed, retrying...");
                key.interestOps(SelectionKey.OP_WRITE);
                return false;
            }
        }

        return loginSent;
    }

    private byte[] getLoginMessage() {
        String loginJson = "{\"uSer\": \"SmartWatch01\", \"pWd\":\"Smart1559\", \"secret1\":\"Device4739\"}";
        return loginJson.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateDeviceData(String deviceId) {
        JsonObject json = new JsonObject();
        json.addProperty("vEh", deviceId);
        json.addProperty("lT", 25.750635 + Math.random());
        json.addProperty("lOn", 81.169144 + Math.random());
        json.addProperty("tIme", System.currentTimeMillis() / 1000);
        json.addProperty("sPeed", (int) (Math.random() * 10));
        json.addProperty("heartBeat", String.valueOf(60 + (int)(Math.random() * 40)));
        json.addProperty("steps", (int) (Math.random() * 10000));
        json.addProperty("battery", 70 + (int) (Math.random() * 30));
        json.addProperty("signalStrength", -70 + (int) (Math.random() * 10));
        json.addProperty("mode", "walk");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int write(SelectionKey key, byte[] data) {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            return channel.write(ByteBuffer.wrap(data));
        } catch (IOException e) {
            logger.error("Write failed", e);
            try {
                channel.close();
                key.cancel();
            } catch (IOException ex) {
                logger.error("Error closing channel after write failure", ex);
            }
            return -1;
        }
    }

    private byte[] read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        readBuffer.clear();
        int length = channel.read(readBuffer);
        if (length == -1) {
            logger.info("Server closed connection");
            channel.close();
            key.cancel();
            return null;
        }
        if (length == 0) return null;

        readBuffer.flip();
        byte[] data = new byte[length];
        readBuffer.get(data);
        return data;
    }

    private void close() {
        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
                logger.info("Selector closed");
            }
        } catch (IOException e) {
            logger.error("Error closing selector", e);
        }
    }
}
