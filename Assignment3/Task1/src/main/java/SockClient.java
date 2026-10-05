import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.Scanner;

/**
 * Interactive client for Assignment 3, Task 1.
 */
class SockClient {
  static Socket sock = null;
  static String host = "localhost";
  static int port = 8888;
  static OutputStream out;
  static ObjectOutputStream os;
  static DataInputStream in;

  public static void main (String args[]) {
    if (args.length != 2) {
      System.out.println("Expected arguments: <host(String)> <port(int)>");
      System.exit(1);
    }

    try {
      host = args[0];
      port = Integer.parseInt(args[1]);
    } catch (NumberFormatException nfe) {
      System.out.println("[Port|sleepDelay] must be an integer");
      System.exit(2);
    }

    try {
      connect(host, port);
      System.out.println("Client connected to server.");
      Scanner scanner = new Scanner(System.in);
      boolean requesting = true;

      while (requesting) {
        System.out.println("What would you like to do: 1 - echo, 2 - add, 3 - string concatenation, 4 - currency (0 to quit)");
        int choice = Integer.parseInt(scanner.nextLine());
        JSONObject json = new JSONObject();

        switch(choice) {
          case 0:
            System.out.println("Choose quit. Thank you for using our services. Goodbye!");
            requesting = false;
            break;
          case 1:
            System.out.println("Choose echo, which String do you want to send?");
            json.put("type", "echo");
            json.put("data", scanner.nextLine());
            break;
          case 2:
            System.out.println("Choose add, enter first number:");
            json.put("type", "add");
            json.put("num1", scanner.nextLine());
            System.out.println("Enter second number:");
            json.put("num2", scanner.nextLine());
            break;
          case 3:
            System.out.println("Choose string concatenation, enter first string:");
            String str1 = scanner.nextLine();
            System.out.println("Enter second string:");
            String str2 = scanner.nextLine();
            json.put("type", "stringconcatenation");
            json.put("string1", str1);
            json.put("string2", str2);
            break;
          case 4:
            System.out.println("Choose currency conversion, enter amount:");
            double amount = Double.parseDouble(scanner.nextLine());
            System.out.println("Enter source currency (USD, EUR, GBP):");
            String from = scanner.nextLine().trim();
            System.out.println("Enter target currency (USD, EUR, GBP):");
            String to = scanner.nextLine().trim();
            json.put("type", "currency");
            json.put("amount", amount);
            json.put("from", from);
            json.put("to", to);
            break;
          default:
            System.out.println("Unknown menu choice.");
            continue;
        }

        if(!requesting) {
          continue;
        }

        os.writeObject(json.toString());
        os.flush();

        String incoming = in.readUTF();
        JSONObject res = new JSONObject(incoming);
        System.out.println("Got response: " + res);

        if (res.getBoolean("ok")) {
          String type = res.getString("type");
          if (type.equals("echo")) {
            System.out.println(res.getString("echo"));
          } else if (type.equals("add")) {
            System.out.println("Result: " + res.getInt("result"));
          } else if (type.equals("stringconcatenation")) {
            System.out.println("Result: " + res.getString("result"));
          } else if (type.equals("currency")) {
            System.out.println(res.getDouble("amount") + " " + res.getString("from")
                + " = " + res.getDouble("result") + " " + res.getString("to")
                + " (rate " + res.getDouble("rate") + ")");
          }
        } else {
          System.out.println(res.getString("message"));
        }
      }

      overandout();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void overandout() throws IOException {
    if (in != null) in.close();
    if (os != null) os.close();
    if (sock != null) sock.close();
  }

  public static void connect(String host, int port) throws IOException {
    sock = new Socket(host, port);
    out = sock.getOutputStream();
    os = new ObjectOutputStream(out);
    in = new DataInputStream(sock.getInputStream());
  }
}
