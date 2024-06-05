import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Random;

public class Battle implements Runnable {
    private final Socket whiteSocket;
    private String whiteUsername;
    private final Socket blackSocket;
    private String blackUsername;
    private Connection databaseCon;
    private boolean didGameEnd;
    public Battle(Socket socket1, String username1, Socket socket2, String username2, Connection databaseCon) throws SocketException {
        Random rand = new Random();
        this.databaseCon = databaseCon;
        this.didGameEnd = false;
        //0 - White, 1 - Black
        int startingColor = rand.nextInt(1);
        socket1.setSoTimeout(100);
        socket2.setSoTimeout(100);
        if(startingColor == 0){
            this.whiteSocket = socket1;
            this.whiteUsername = username1;
            this.blackSocket = socket2;
            this.blackUsername = username2;
        }
        else{
            this.whiteSocket = socket2;
            this.whiteUsername = username2;
            this.blackSocket = socket1;
            this.blackUsername = username1;
        }

    }

    @Override
    public void run() {
        writeMessage(1, "Starting as White");
        writeMessage(2, "Starting as Black");
        while (true) {
            String message = "";
            while(message.isEmpty()){
                message = getMessage(1);
            }
            System.out.println("White sent: " + message);

            writeMessage(2, message);
            if (message.equals("Abandoned") || didGameEnd){
                try {
                    disconnectUsers();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            message = "";
            while(message.isEmpty()){
                message = getMessage(2);
            }

            System.out.println("Black sent: " + message);

            writeMessage(1, message);
            if (message.equals("Abandoned") || didGameEnd){
                try {
                    disconnectUsers();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }
    }
    public void writeMessage(int socketNumber, String message){
        if(socketNumber == 1){
            // Write the message to white.
            try {
                whiteSocket.getOutputStream().write(message.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else{
            // Write the message to black.
            try {
                blackSocket.getOutputStream().write(message.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public String getMessage(int socketNumber) {
        Socket socket = whiteSocket;
        if(socketNumber == 2){
            socket = blackSocket;
        }
        byte[] message = new byte[4096];
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
            return "";
        }
        System.out.println("BYTES RECEIVED FROM" + socketNumber + " IS " + bytesReceived);
        // Convert the message to a string.
        String finalMessage = new String(message, 0, bytesReceived);
        String [] userMessage = finalMessage.split("%");
        if(userMessage.length > 1 && userMessage[0].equals("GameEnded")){
            System.out.println("Marked Game Ended");
            didGameEnd = true;
            finalMessage = userMessage[1];
        }

        return finalMessage;
    }
    public void disconnectUsers() throws SQLException, IOException {
        PreparedStatement statement = databaseCon.prepareStatement("UPDATE User SET isConnected = ? WHERE username = ?");
        statement.setBoolean(1, false); // isConnected
        System.out.println("Sets " + whiteUsername + " as disconnected");
        statement.setString(2, whiteUsername); // isConnected
        statement.executeUpdate();
        System.out.println("Client " + whiteUsername + " disconnected");
        Main.allThreads.remove(whiteUsername);
        Main.socketThread.remove(whiteUsername);

        statement = databaseCon.prepareStatement("UPDATE User SET isConnected = ? WHERE username = ?");
        statement.setBoolean(1, false); // isConnected
        System.out.println("Sets " + blackUsername + " as disconnected");
        statement.setString(2, blackUsername); // isConnected
        statement.executeUpdate();
        System.out.println("Client " + blackUsername + " disconnected");
        Main.allThreads.remove(blackUsername);
        Main.socketThread.remove(blackUsername);
    }
}
