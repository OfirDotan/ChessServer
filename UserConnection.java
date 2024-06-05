import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserConnection implements Runnable {
    private final String username;
    private final Socket chessSocket;
    private String otherUsername;
    private Socket otherChessSocket;
    private final Connection connection;
    //AtomicBoolean receivedBattleAccepted;
    volatile boolean receiverAccepted;
    volatile boolean initiatorAccepted;
    private volatile boolean shouldRun;
    private static final Object battleAcceptedLock = new Object();

    public UserConnection(String username, Socket socket, Connection connection) {
        this.username = username;
        this.chessSocket = socket;
        this.connection = connection;
        this.receiverAccepted = false;
        this.initiatorAccepted = false;
        this.shouldRun = true;
    }

    public void run() {
        System.out.println("Entered User Connection");
        boolean didStartGame = false;
        while (!didStartGame && shouldRun) {

            if (!chessSocket.isConnected()) {
                System.out.println("Client " + username + " disconnected");
            }
            try {
                String messageString = receive(chessSocket);
                if (!messageString.isEmpty()) {
                    if(messageString.equals("Closed")){
                        Thread.currentThread().interrupt();
                        break;
                    }
                    else if (messageString.equals("Disconnected")) {
                        disconnectCurrentUser();
                        break;
                    } else if (messageString.equals("Gimmie Users")) {
                        System.out.println("Gives all users");
                        String usernames = Main.getAvailableUsers(username);
                        chessSocket.getOutputStream().write(usernames.getBytes());
                    }
                    else {
                        otherUsername = messageString;
                        System.out.println("Looking for user: " + otherUsername + ". The user who is looking is " + username);
                        PreparedStatement statement = connection.prepareStatement("SELECT isConnected FROM User WHERE username = ?");
                        statement.setString(1, otherUsername);
                        ResultSet resultSet = statement.executeQuery();//The user exists and is connected
                        if (resultSet.next()) {
                            if (resultSet.getBoolean("isConnected")) {
                                otherChessSocket = Main.connectedSockets.get(otherUsername);
                                didStartGame = true;
                                System.out.println("Found user and starting the Battle Thread creation process");
                            } else {
                                System.out.println("User is offline");
                                byte[] messageBytes = "User is offline".getBytes();
                                chessSocket.getOutputStream().write(messageBytes);
                            }
                        } else {
                            System.out.println("User not found");
                        }
                    }
                }
            }
            catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
        }
        if (didStartGame) {
            System.out.println("Closing other thread, waiting...");
            synchronized (Main.allThreads.get(otherUsername)){
                Main.socketThread.get(otherUsername).stopRunning();
            }

            try {
                Main.allThreads.get(otherUsername).join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Main.allThreads.remove(otherUsername);
            Main.socketThread.remove(otherUsername);

            System.out.println("Sending first verification");
            byte[] messageBytes = "Battle Started".getBytes();

            try {
                chessSocket.getOutputStream().write(messageBytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                otherChessSocket.getOutputStream().write(messageBytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            CountDownLatch latch = new CountDownLatch(2);

            // Create Thread 1 for receiving from chessSocket
            Thread thread1 = new Thread(() -> {
                String response = null;
                try {
                    response = receive(chessSocket);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (response.equals("Battle Accepted")) {
                    System.out.println("Thread 1 got Battle Accepted");
                    initiatorAccepted = true;
                    latch.countDown();
                    System.out.println(latch.getCount() + "Count from 1");
                }
            });
            thread1.start();
            // Create Thread 2 for receiving from otherChessSocket
            Thread thread2 = new Thread(() -> {
                String response = null;
                try {
                    response = receive(otherChessSocket);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println("Response 2 " + response);
                if (response.equals("Battle Accepted")) {
                    receiverAccepted = true;
                    latch.countDown();
                    System.out.println(latch.getCount() + "Count from 2");
                }
            });
            thread2.start();
            try {
                // Wait until both Thread1 and Thread2 finish
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("int: " + initiatorAccepted);
            System.out.println("rec: " + receiverAccepted);

            if (initiatorAccepted && receiverAccepted) {
                Battle battle = null;
                try {
                    battle = new Battle(chessSocket, username, otherChessSocket, otherUsername, connection);
                } catch (SocketException e) {
                    throw new RuntimeException(e);
                }
                Thread battleThread = new Thread(battle);
                battleThread.start();
                if (battleThread.isAlive()) {
                    //Marks them as offline for new battles:
                    try {
                        PreparedStatement statement = connection.prepareStatement("UPDATE User SET isConnected = ? WHERE username = ?");
                        statement.setBoolean(1, false); // isConnected
                        statement.setString(2, username);
                        statement.executeUpdate();

                        statement = connection.prepareStatement("UPDATE User SET isConnected = ? WHERE username = ?");
                        statement.setBoolean(1, false); // isConnected
                        statement.setString(2, otherUsername);
                        statement.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }

                    System.out.println("Started Battle between " + username + " and " + otherUsername);
                }
            }
        }
    }
    public void stopRunning(){
        shouldRun = false;
    }
    public String receive(Socket socket) throws SQLException, IOException {
        byte[] message = new byte[1024];
        int bytesReceived = 0;
        try {
            socket.setSoTimeout(100);
            bytesReceived = socket.getInputStream().read(message);
        } catch (SocketTimeoutException e) {
            return "";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (bytesReceived == -1){
            disconnectCurrentUser();
            return "";
        }
        // Convert the message to a string.
        return new String(message, 0, bytesReceived);
    }
    public void disconnectCurrentUser() throws SQLException, IOException {
        stopRunning();
        PreparedStatement statement = connection.prepareStatement("UPDATE User SET isConnected = ? WHERE username = ?");
        statement.setBoolean(1, false); // isConnected
        System.out.println("Sets " + username + " as disconnected");
        statement.setString(2, username); // isConnected
        statement.executeUpdate();
        System.out.println("Client " + username + " disconnected");
        chessSocket.close();
        Main.allThreads.remove(username);
        Main.socketThread.remove(username);
    }
}
