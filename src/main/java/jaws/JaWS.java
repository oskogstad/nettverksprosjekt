package jaws;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.*;
import java.security.*;

public class JaWS {
    public static final int PORT=40506;

    public static void main(String[] args) throws IOException {

        //Utilities
        
        final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        
        Base64.Encoder b64encoder = Base64.getEncoder();

        MessageDigest sha1digester = null;
        try {
            sha1digester = MessageDigest.getInstance("SHA-1");
        }
        catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        // list of all connections
        ArrayList<Connection> connections = new ArrayList();

        final ServerSocket socketServer = new ServerSocket(PORT);

        // ShutdownHook, catches any interrupt signal and closes all threads
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                try {
                    // Close all threads
                    System.out.println("Number of threads: " + connections.size());

                    for (Connection c : connections) {
                        c.interrupt();
                    }

                    if (socketServer != null) socketServer.close();

                    System.out.println("All done. Bye!");

                } catch(Exception e) {
                    System.out.println("Thread shutdown failed ...");
                    e.printStackTrace();
                }
            }
        }));

        System.out.println("Server now listening on port " + PORT);
        while(true) {
            try {
                // Waiting for connections
                Socket socket = socketServer.accept();
				System.out.println("Incomming connection ...");

				ArrayList<String> httpReq = new ArrayList();

                try {
                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                    PrintWriter out = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                    // Adding httpReq to string array
                    String s;
                    while((s=in.readLine()) != null) {
                        if(s.isEmpty()) {
                            break;
                        }
                        httpReq.add(s);
                    }

                    String upgrade = null;
                    String connection = null;
                    String wsKey = null;
                    String[] wsProtocol = null;
                    int wsVersion = -1;
                    for (String line : httpReq) {
                        System.out.println(line);

                        String[] parts = line.split(": ");
                        if (parts.length == 1) {
                            // Should we check the GET ... line here?    
                        }
                        else {
                            String key = parts[0];
                            String val = parts[1];

                            if(key.equalsIgnoreCase("upgrade")) {
                                upgrade = val;
                            }
                            else if(key.equalsIgnoreCase("connection")) {
                                connection = val;
                            }
                            else if(key.equalsIgnoreCase("sec-websocket-key")) {
                                wsKey = val; 
                            }
                            else if(key.equalsIgnoreCase("sec-websocket-protocol")) {
                                wsProtocol = val.split(",");
                            }
                            else if(key.equalsIgnoreCase("sec-websocket-version")) {
                                wsVersion = Integer.parseInt(val);
                            }
                        }
                    }

                    boolean websocket = false;
                    if (
                            upgrade != null && upgrade.equalsIgnoreCase("websocket") &&
                            connection != null && connection.equalsIgnoreCase("upgrade") &&
                            wsKey != null)
                    {
                        websocket = true;
                        // Send handshake response
                        

                        String acceptKey = b64encoder.encodeToString(
                                sha1digester.digest((wsKey+GUID).getBytes()));
                        
                        out.write(
                                "HTTP/1.1 101 Switching Protocols\r\n"+
                                "Upgrade: websocket\r\n"+
                                "Connection: Upgrade\r\n"+
                                "Sec-WebSocket-Accept: "+acceptKey+
                                "\r\n\r\n");
                        out.flush();

                        System.out.println("Handshake sent");
                    }
                    else {
                        out.write(
                                "HTTPS/1.1 503 Connection Refused\r\n"+
                                "\r\n\r\n"
                                );
                        out.flush();

                        System.out.println("Connection refused");
                    }

                    if(websocket) {
                        Connection con= new Connection(socket);
                        connections.add(con);
                        con.start();
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("IO error on socket creation");
                }
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("Socket accept failed");
            }
        }
    }
}