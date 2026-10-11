import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;


public class ServerTest {

    Socket sock;
    OutputStream out;
    ObjectOutputStream os;

    DataInputStream in;


    // Establishing a connection to the server, make sure you start the server on localhost and 8888
    @org.junit.Before
    public void setUp() throws Exception {
        // Establish connection to server and create in/out streams
        sock = new Socket("localhost", 8888); // connect to host and socket

        // get output channel
        out = sock.getOutputStream();

        // create an object output writer (Java only)
        os = new ObjectOutputStream(out);

        // setup input stream
        in = new DataInputStream(sock.getInputStream());
    }

    @org.junit.After
    public void close() throws Exception {
        if (out != null)  out.close();
        if (sock != null) sock.close();
    }

        @Test
    public void addRequest() throws IOException {
        // create a correct req for server
        JSONObject req = new JSONObject();
        req.put("type", "add");
        req.put("num1", "1");
        req.put("num2", "2");

        // write the whole message
        os.writeObject(req.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();

        String i = (String) in.readUTF();
        // assuming I get correct JSON back
        JSONObject res = new JSONObject(i);

        // test response
        assertTrue(res.getBoolean("ok"));
        assertEquals("add", res.getString("type"));
        assertEquals(3, res.getInt("result"));

        // Wrong request to server num2 missing
        JSONObject req2 = new JSONObject();
        req2.put("type", "add");
        req2.put("num1", "1");
        // write the whole message
        os.writeObject(req2.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();

        i = (String) in.readUTF();
        // assuming I get correct JSON back
        res = new JSONObject(i);

        System.out.println(res);

        // test response
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field num2 does not exist in request", res.getString("message"));

        // Wrong request to server num1 missing
        JSONObject req3 = new JSONObject();
        req3.put("type", "add");
        req3.put("num2", "1");
        // write the whole message
        os.writeObject(req3.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();

        i = (String) in.readUTF();
        // assuming I get correct JSON back
        res = new JSONObject(i);

        System.out.println(res);

        // test response
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field num1 does not exist in request", res.getString("message"));

        // Wrong request to server num1 num2 missing
        JSONObject req4 = new JSONObject();
        req4.put("type", "add");
        // write the whole message
        os.writeObject(req4.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();

        i = (String) in.readUTF();
        // assuming I get correct JSON back
        res = new JSONObject(i);

        System.out.println(res);

        // test response
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field num1 does not exist in request", res.getString("message"));

        // Wrong request to server num2 missing
        JSONObject req5 = new JSONObject();
        req5.put("type", "add");
        req5.put("num1", "hello");
        req5.put("num2", "2");
        // write the whole message
        os.writeObject(req5.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();

        i = (String) in.readUTF();
        // assuming I get correct JSON back
        res = new JSONObject(i);

        System.out.println(res);

        // test response
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field num1/num2 needs to be of type: int", res.getString("message"));
    }

    @Test
    public void echoRequest() throws IOException {
        // valid request with data
        JSONObject req1 = new JSONObject();
        req1.put("type", "echo");
        req1.put("data", "gimme this back!");
        // write the whole message
        os.writeObject(req1.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();
        String i = (String) in.readUTF();
        // assuming I get correct JSON back
        JSONObject res = new JSONObject(i);
        // test response
        assertTrue(res.getBoolean("ok"));
        assertEquals("echo", res.getString("type"));
        assertEquals("Here is your echo: gimme this back!", res.getString("echo"));

        // Invalid request - no data sent
        JSONObject req2 = new JSONObject();
        req2.put("type", "echo");
        // write the whole message
        os.writeObject(req2.toString());
        // make sure it wrote and doesn't get cached in a buffer
        os.flush();
        i = (String) in.readUTF();
        // assuming I get correct JSON back
        res = new JSONObject(i);
        System.out.println(res);
        // test response
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field data does not exist in request", res.getString("message"));
    }

    @Test
    public void notJSON() throws IOException {
        // create a correct req for server
        os.writeObject("a");

        String i = (String) in.readUTF();
        // assuming I get correct JSON back
        JSONObject res = new JSONObject(i);

        // test response
        assertFalse(res.getBoolean("ok"));
        assertEquals("req not JSON", res.getString("message"));

        // calling the other test to make sure server continues to work and the "continue" does what it is supposed to do
        addRequest();
    }

    private JSONObject send(JSONObject req) throws IOException {
        os.writeObject(req.toString());
        os.flush();
        return new JSONObject(in.readUTF());
    }

    @Test
    public void stringConcatenationRequest() throws IOException {
        JSONObject req = new JSONObject();
        req.put("type", "stringconcatenation");
        req.put("string1", "Hello ");
        req.put("string2", "World");

        JSONObject res = send(req);
        assertTrue(res.getBoolean("ok"));
        assertEquals("stringconcatenation", res.getString("type"));
        assertEquals("Hello World", res.getString("result"));

        JSONObject invalid = new JSONObject();
        invalid.put("type", "stringconcatenation");
        invalid.put("string1", "Hello");
        res = send(invalid);
        assertFalse(res.getBoolean("ok"));
        assertEquals("stringconcatenation", res.getString("type"));
        assertEquals("Field string2 does not exist in request", res.getString("message"));
    }

    @Test
    public void currencyRequest() throws IOException {
        JSONObject req = new JSONObject();
        req.put("type", "currency");
        req.put("amount", 100.0);
        req.put("from", "USD");
        req.put("to", "EUR");

        JSONObject res = send(req);
        assertTrue(res.getBoolean("ok"));
        assertEquals("currency", res.getString("type"));
        assertEquals("USD", res.getString("from"));
        assertEquals("EUR", res.getString("to"));
        assertEquals(100.0, res.getDouble("amount"), 0.0001);
        assertEquals(92.0, res.getDouble("result"), 0.0001);
        assertEquals(0.92, res.getDouble("rate"), 0.0001);

        JSONObject invalid = new JSONObject();
        invalid.put("type", "currency");
        invalid.put("amount", 25.0);
        invalid.put("from", "CAD");
        invalid.put("to", "USD");
        res = send(invalid);
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field 'from' must be one of: USD, EUR, GBP", res.getString("message"));

        JSONObject negative = new JSONObject();
        negative.put("type", "currency");
        negative.put("amount", -1.0);
        negative.put("from", "USD");
        negative.put("to", "GBP");
        res = send(negative);
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field 'amount' cannot be negative", res.getString("message"));
    }

    @Test
    public void analyzerRequests() throws IOException {
        JSONObject wordCount = new JSONObject();
        wordCount.put("type", "analyzer");
        wordCount.put("action", "wordcount");
        wordCount.put("text", "one two three");
        JSONObject res = send(wordCount);
        assertTrue(res.getBoolean("ok"));
        assertEquals("analyzer", res.getString("type"));
        assertEquals("wordcount", res.getString("action"));
        assertEquals(3, res.getInt("count"));

        JSONObject charCount = new JSONObject();
        charCount.put("type", "analyzer");
        charCount.put("action", "charcount");
        charCount.put("text", "hello world");
        res = send(charCount);
        assertTrue(res.getBoolean("ok"));
        assertEquals(11, res.getInt("count"));

        JSONObject search = new JSONObject();
        search.put("type", "analyzer");
        search.put("action", "search");
        search.put("text", "banana");
        search.put("find", "ana");
        res = send(search);
        assertTrue(res.getBoolean("ok"));
        assertTrue(res.getBoolean("found"));
        assertEquals(2, res.getInt("count"));
        assertEquals(1, res.getJSONArray("positions").getInt(0));
        assertEquals(3, res.getJSONArray("positions").getInt(1));

        JSONObject badSearch = new JSONObject();
        badSearch.put("type", "analyzer");
        badSearch.put("action", "search");
        badSearch.put("text", "banana");
        res = send(badSearch);
        assertFalse(res.getBoolean("ok"));
        assertEquals("Field 'find' does not exist in request", res.getString("message"));
    }

}