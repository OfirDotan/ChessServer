import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserConnection implements Runnable {
    private final String username;
    private final Socket chessSocket;
    private String otherUsername;
    private Socket otherChessSocket;
    private final Connection connection;
    private  volatile  boolean receivedBattleAccepted;
    private volatile boolean shouldRun;
    public UserConnection(String username, Socket socket, Connection connection) {
        this.username = username;
        this.chessSocket = socket;
        this.connection = connection;
        shouldRun = true;
        receivedBattleAccepted = false;
    }
    public void run() {
        System.out.println("Entered User Connection");
        boolean didStartGame = false;

        while (!didStartGame && shouldRun) {
            if(!chessSocket.isConnected()){
                        System.out.println("Client " + username + " disconnected");
            }
            try {
                // Receive a message from the client.
                byte[] message = new byte[1024];
                int bytesReceived = chessSocket.getInputStream().read(message);
                if(bytesReceived > 0){
                    // Convert the message to a string.
                    String messageString = new String(message, 0, bytesReceived);
                    if(messageString.equals("Disconnected")){
                        PreparedStatement statement = connection.prepareStatement("UPDATE User SET isConnected = ? WHERE username = ?");
                        statement.setBoolean(1, false); // isConnected
                        System.out.println("Sets " + username + " as disconnected");
                        statement.setString(2, username); // isConnected
                        statement.executeUpdate();
                        System.out.println("Client " + username + " disconnected");

                        chessSocket.close();
                        Thread.currentThread().interrupt();
                        break;

                    }
                    else if(messageString.equals("Gimmie Users")){
                        System.out.println("Gives all users");
                        String usernames = Main.getAvailableUsers(username);
                        chessSocket.getOutputStream().write(usernames.getBytes());
                    }
                    else if (messageString.equals("Battle Accepted")) {
                        receivedBattleAccepted = true;
                        return;
                    }
                    else{
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
                            }
                            else {
                                System.out.println("Error sending match");
                                byte[] response = "Error sending match".getBytes();
                                chessSocket.getOutputStream().write(response);
                            }
                        }
                        else{
                            System.out.println("User not found");
                            byte[] response = "User not found".getBytes();
                            chessSocket.getOutputStream().write(response);
                        }
                    }
                }
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
            }
            if (didStartGame) {
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

                // Receive a message from the client.
                String string2 = "";
                String string1 = receive(chessSocket);
                if (Main.socketThread.get(otherUsername).receivedBattleAccepted){
                    string2 = "Battle Accepted";
                }
                else{
                    string2 = receive(otherChessSocket);
                    Main.socketThread.get(otherUsername).shouldRun = false;
                }
                System.out.println("I got " + string1 + " before checking");
                System.out.println("I got " + string2 + " before checking");
                if (string1.equals("Battle Accepted") && string1.equals(string2)) {
//                    byte[] response = "Are you ready for the match".getBytes();
//                    //Asks both sockets if they are in the match
//                    try {
//                        chessSocket.getOutputStream().write(response);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    try {
//                        otherChessSocket.getOutputStream().write(response);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//
//                    //Checks their answers
//
//                    // Receive a message from the client.
//                    byte[] message = new byte[1024];
//                    int bytesReceived = 0;
//                    try {
//                        bytesReceived = chessSocket.getInputStream().read(message);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    // Convert the message to a string.
//                    string1 = new String(message, 0, bytesReceived);
                      System.out.println("Answer 1 " + string1);
//
//                    // Receive a message from the client.
//                    message = new byte[1024];
//                    try {
//                        bytesReceived = otherChessSocket.getInputStream().read(message);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    // Convert the message to a string.
//                    string2 = new String(message, 0, bytesReceived);
                    System.out.println("Answer 2 " + string2);
//                    try {
//                        Thread.sleep(100); // Adjust the delay as needed
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                    //if (string1.equals(string2) && string1.equals("Yes")) {
                        Battle battle = new Battle(chessSocket, username, otherChessSocket, otherUsername);
                        Thread battleThread = new Thread(battle);
                        battleThread.start();
                        if (battleThread.isAlive()) {
                            System.out.println("Started Battle between " + username + " and " + otherUsername);
                        }
                    //}
                }
                else {
                    didStartGame = false;
                }
            }
        }
    }
    public String receive(Socket socket){
        // Receive a message from the client.
        String string = "";
        // Receive a message from the client.
        byte[] message = new byte[1024];
        int bytesReceived = 0;
        try {
            bytesReceived = socket.getInputStream().read(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Convert the message to a string.
        return string = new String(message, 0, bytesReceived);
    }
}
