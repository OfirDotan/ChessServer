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

   public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
      connectToSQL();
      ServerSocket serverSocket = new ServerSocket(8452);
      System.out.println("Server Online.");

      while(true) {
         while(true) {
            Socket socket = serverSocket.accept();
            System.out.println("New socket Connected.");
            byte[] message = new byte[1024];
            int bytesReceived = socket.getInputStream().read(message);
            String messageString = new String(message, 0, bytesReceived);
            String[] typeAndFields = messageString.split("%");
            //Signup
            if(typeAndFields[0] == "s"){

               String[] usernamePassword = messageString.split("#");

               statement = sqlConnection.prepareStatement("SELECT * FROM User WHERE username = ?");
               statement.setString(1, usernamePassword[0]);
               ResultSet resultSet = statement.executeQuery();

               if(resultSet.next()){
                     response = "UsernameTaken".getBytes();
                     socket.getOutputStream().write(response);
               }
               else{
                  statement = sqlConnection.prepareStatement("INSERT INTO User (username, user_password, ip, port, isConnected) VALUES (?, ?, ?, ?, ?)");
                  statement.setString(1, usernamePassword[0]); //Username
                  statement.setString(2, usernamePassword[1]); // Password
                  statement.setString(3, socket.getInetAddress().getHostAddress()); //Last known IP
                  statement.setInt(4, socket.getPort()); // Last known port
                  statement.setBoolean(5, false); // isConnected
                  statement.executeUpdate();

                  response = "SignUpAccepted".getBytes();
                  socket.getOutputStream().write(response);
               }
               socket.close();
            }
            //Login
            else {
               String[] usernamePassword = messageString.split("#");

               PreparedStatement statement = sqlConnection.prepareStatement("SELECT user_password FROM User WHERE username = ?");
               statement.setString(1, usernamePassword[0]);
               ResultSet resultSet = statement.executeQuery();
               byte[] response;
               if (resultSet.next()) {
                  if (usernamePassword[1].equals(resultSet.getString("user_password"))) {
                     System.out.println("Login Accepted");
                     response = "Login Accepted".getBytes();
                     statement = sqlConnection.prepareStatement("UPDATE User SET port = ?, ip = ?, isConnected = ? WHERE username = ?");
                     statement.setInt(1, socket.getPort());
                     statement.setString(2, socket.getInetAddress().getHostAddress());
                     statement.setBoolean(3, true);
                     statement.setString(4, usernamePassword[0]);
                     statement.executeUpdate();
                     connectedSockets.put(usernamePassword[0], socket);
                     Thread thread = new Thread(new UserConnection(usernamePassword[0], socket, sqlConnection));
                     thread.start();
                     if (thread.isAlive()) {
                        System.out.println("Started Thread");
                     }
                  } else {
                     response = "Wrong password".getBytes();
                  }

                  socket.getOutputStream().write(response);
               }
               else {
                  response = "Username doesn't exist!".getBytes();
                  socket.getOutputStream().write(response);
               }
            }
         }
      }
   }

   public static void connectToSQL() throws SQLException, ClassNotFoundException {
      String url = "jdbc:mysql://localhost:3306/users";
      Class.forName("com.mysql.cj.jdbc.Driver");
      sqlConnection = DriverManager.getConnection(url, "ubuntu", "12345");
   }
}
