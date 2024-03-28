import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Random;

public class Battle implements Runnable {
    private final Socket whiteSocket;
    private String whiteUsername;
    private final Socket blackSocket;
    private String blackUsername;
    public Battle(Socket socket1, String username1, Socket socket2, String username2) throws SocketException {
        Random rand = new Random();
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

            if (message.equals("Battle Over")){
                break;
            }

            writeMessage(2, message);
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
        return new String(message, 0, bytesReceived);
    }
}
