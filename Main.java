import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class Main {
    static Connection sqlConnection;
    static HashMap<String, Socket> connectedSockets = new HashMap();
    static HashMap<String, UserConnection> socketThread = new HashMap();

    static HashMap<String, Thread> allThreads = new HashMap();

    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException, InterruptedException {
        connectToSQL();
        ServerSocket serverSocket = new ServerSocket(8452);
        System.out.println("Server Online.");

        while(true) {
            Socket socket = serverSocket.accept();
            System.out.println("New socket Connected.");
            byte[] message = new byte[1024];
            int bytesReceived = socket.getInputStream().read(message);
            String messageString = new String(message, 0, bytesReceived);
            String[] typeAndFields = messageString.split("%");
            System.out.println(typeAndFields[0]);
            System.out.println(typeAndFields[1]);
            messageString = typeAndFields[1];

            System.out.println(typeAndFields[0]);
            //Signup
            if(typeAndFields[0].equals("s")){
                String[] usernamePassword = messageString.split("#");

                PreparedStatement statement = sqlConnection.prepareStatement("SELECT * FROM User WHERE username = ?");
                statement.setString(1, usernamePassword[0]);
                ResultSet resultSet = statement.executeQuery();

                if(resultSet.next()){
                    byte[] response = "UsernameTaken".getBytes();
                    socket.getOutputStream().write(response);
                }
                else{
                    PreparedStatement statement2 = sqlConnection.prepareStatement("INSERT INTO User (username, user_password, ip, port, isConnected) VALUES (?, ?, ?, ?, ?)");
                    statement2.setString(1, usernamePassword[0]); //Username
                    statement2.setString(2, usernamePassword[1]); // Password
                    statement2.setString(3, socket.getInetAddress().getHostAddress()); //Last known IP
                    statement2.setInt(4, socket.getPort()); // Last known port
                    statement2.setBoolean(5, false); // isConnected
                    statement2.executeUpdate();

                    byte[] response = "SignUpAccepted".getBytes();
                    socket.getOutputStream().write(response);
                }
            }
            //Login
            else {
                String[] usernamePassword = messageString.split("#");
                //Checks if the user is connected
                PreparedStatement statement = sqlConnection.prepareStatement("SELECT isConnected FROM User WHERE username = ?");
                statement.setString(1, usernamePassword[0]);
                ResultSet resultSet = statement.executeQuery();
                boolean isUserLoggedIn = false; // Assuming the user is not logged in by default

                if(resultSet.next()) {
                    isUserLoggedIn = resultSet.getBoolean("isConnected");
                }
                if(!isUserLoggedIn){
                    statement = sqlConnection.prepareStatement("SELECT user_password FROM User WHERE username = ?");
                    statement.setString(1, usernamePassword[0]);
                    resultSet = statement.executeQuery();
                    byte[] response;
                    boolean didAcceptLogin = false;
                    if (resultSet.next()) {
                        if (usernamePassword[1].equals(resultSet.getString("user_password"))) {
                            System.out.println("Server Login Accepted");
                            didAcceptLogin = true;
                            response = "Server Login Accepted".getBytes();
                            statement = sqlConnection.prepareStatement("UPDATE User SET port = ?, ip = ?, isConnected = ? WHERE username = ?");
                            statement.setInt(1, socket.getPort());
                            statement.setString(2, socket.getInetAddress().getHostAddress());
                            statement.setBoolean(3, true);
                            statement.setString(4, usernamePassword[0]);
                            statement.executeUpdate();
                            connectedSockets.put(usernamePassword[0], socket);
                        } else {
                            response = "Wrong password".getBytes();
                        }

                        socket.getOutputStream().write(response);
                        socket.getOutputStream().flush();

                        if (didAcceptLogin){
                            message = new byte[1024];
                            bytesReceived = socket.getInputStream().read(message);
                            messageString = new String(message, 0, bytesReceived);
                            if(messageString.equals("Client Login Accepted")) {
                                System.out.println("Client Login Accepted");
                                String usernames = getAvailableUsers(usernamePassword[0]);
                                socket.getOutputStream().write(usernames.getBytes());
                            }
                            UserConnection userConnection = new UserConnection(usernamePassword[0], socket, sqlConnection);
                            Thread thread = new Thread(userConnection);
                            thread.start();
                            if (thread.isAlive()) {
                                System.out.println("Started Thread");
                            }
                            socketThread.put(usernamePassword[0], userConnection);
                            allThreads.put(usernamePassword[0], thread);
                        }
                    }
                    else {
                        response = "Username doesn't exist!".getBytes();
                        socket.getOutputStream().write(response);
                        socket.getOutputStream().flush();
                    }
                }
                else{
                    byte[] response = "User Already Logged In".getBytes();
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                }
            }
        }
    }
    public static String getAvailableUsers(String currUsername) throws SQLException {
        PreparedStatement statement = sqlConnection.prepareStatement("SELECT username FROM User WHERE isConnected = ? AND username <> ?");
        statement.setBoolean(1, true);
        statement.setString(2, currUsername);
        ResultSet resultSet = statement.executeQuery();
        String usernames = "";
        while (resultSet.next()) {
            // Retrieve username from the result set
            String username = resultSet.getString("username");

            // Add username to the list
            usernames += username + ",";
        }
        if(usernames.length() - 1 < 0){
            usernames = "Empty";
        }
        else{
            usernames = usernames.substring(0, usernames.length() - 1);
        }
        return usernames;
    }
    public static void connectToSQL() throws SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/users";
        Class.forName("com.mysql.cj.jdbc.Driver");
        sqlConnection = DriverManager.getConnection(url, "ubuntu", "12345");
    }
}
