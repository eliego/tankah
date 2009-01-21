import java.net.URL;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Date;
import java.util.Vector;
import java.sql.Timestamp;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.apache.commons.lang.RandomStringUtils;
// WAITFORBEGINNINGSTRING -> TID
public class Tankah {
  private static ThreadGroup searchers = new ThreadGroup("Searchers");
  private final static File CONFIGFILE = new File("c:\\tankah.conf");
  public static void main(String[] args) {
    try {
      // Check arguments
      if (args.length != 3 || args[0].equalsIgnoreCase("-h")) {
	System.out.println("Usage: Tankah <search expr> <min size> <min download speed>\nSearch expression must be at least 3 chars");
	System.exit(0);
      }
      else if (args[0].length() < 3)
	throw new Error("Search expression missing or too short. -h for usage information!", 3);
      
      // Init config
      Config.init(CONFIGFILE);
      
      // Start searchers
      debug("Starting "+Config.getProp("threads")+" searcher threads");
      try {
	for (int i = 0; i < Integer.parseInt(Config.getProp("threads")); i++)
	  new Searcher(searchers, args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
      } catch (NumberFormatException e) {
	throw new Error("Size or speed missing or invalid. -h for usage information!", 3);
      }
      
      
    } catch (Error e) {
      error(e);
    } catch (NumberFormatException e) {
      error(new Error("Invalid number of threads. See "+CONFIGFILE, 3));
    } 
    
  }
  
  public static void finito() { /*ÄTODO!!!!*/ }
  
  public static void error(Error e) {
    switch (e.getLevel()) {
    case 1:
      debug(e.getMess());
      break;
    case 2:
      System.out.println("ERROR LEVEL 2: "+e.getMess());
      break;
    case 3:
      System.out.println("ERROR LEVEL 3: "+e.getMess()+"\nEXITING");
      System.exit(-1);
      break;
    default:
      System.out.println("ERROR OF UNDETERMINED LEVEL: "+e.getMess()+"\nEXITING");
      System.exit(-1);
      break;
    }
    
    return;
  }
  
  public static void debug(String mess) {
    try { 
      if ((Boolean.valueOf(Config.getProp("displayDebug"))).booleanValue())
	System.out.println("DEBUG: "+mess);
    } catch (Error e) {
      System.out.println("Please set the displayDebug-setting in "+CONFIGFILE.toString());
    }
    return;
  }
}

abstract class Config {
  private static HashMap props;
  private static File file;
  public static void init(File file) throws Error {
    // Make sure the files is readable
    if (!file.canRead())
      throw new Error(file.toString()+" doesn't exist or isn't readable", 3);
    
    props = new HashMap();
    Config.file = file;
    BufferedReader in = null;
    
    try {
      // Read file
      String value = new String();
      String key = new String();
      int eq = 0;
      int temp = 0;
      int temp2 = 0;
      int num = 0;
      in = new BufferedReader(new FileReader(file));
      
      while ((value = in.readLine()) != null) {
	num++;
	if (value.startsWith("#"))
	  continue;
	if ((temp = eq = value.indexOf("=")) == -1)
	  throw new Error(file.toString() + ": syntax error on line "+String.valueOf(num), 3); 
	for (temp--; temp >= 0 && !isChar(value.charAt(temp)); temp--) ;
	for (temp2 = temp; temp2 >= 0 && isChar(value.charAt(temp2)); temp2--) ;
	key = value.substring(temp2 + 1, temp + 1);
	for (temp = eq + 1; temp <= value.length() - 1 && !isChar(value.charAt(temp)); temp++) ;
	for (temp2 = temp; temp2 <= value.length() - 1 && value.charAt(temp2) != ' ' && value.charAt(temp2) != '\t'; temp2++) ;
	value = value.substring(temp, temp2);
	props.put(key, value);
      }
      
    } catch (IOException e){
      throw new Error("Error when reading "+file.toString(), 3);
    } finally {
      try {
	if (in != null)
	  in.close();
      } catch (IOException e) {
	throw new Error("Couln't close "+file.toString(),2);
      }
    }
    
    return;
  }
  
  public static String getProp(String prop) throws Error {
    String val;
    if ((val = (String)props.get(prop)) == null)
      throw new Error("Property "+prop+" couln't be found in "+file, 3);
    return val;
  }
  
  private static boolean isChar(char c) {
    if ((c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || (c == 95))
      return true;
    else
      return false;
  }
  
  public static String getFileName() {
    return file.toString();
  }
  
}

class Error extends Throwable {
  private int level;
  private String mess;
  public Error(String mess, int level) {
    this.level = level;
    this.mess = mess;
    return;
  }
  
  public String getMess() {
    return mess;
  }
  
  public int getLevel() {
    return level;
  }
}

class Searcher extends Thread {
  private static int num = 0;
  private String file;
  private String nick;
  private String addr;
  private String pre;
  private int size;
  private int speed;
  private int port = 411;
  
  public Searcher(ThreadGroup group, String file, int size, int speed) {
    super(group, String.valueOf(++num));
    this.size = size;
    this.speed = speed;
    this.file = file;
    this.start();
  }
  
  public void run() {
    int temp;
    String strin;
    SeparatedBufferedReader in = null;
    OutputStream out = null;
    long now = 0;
    while ((addr = HubList.getHub()) != null) {
      try {
	Tankah.debug("Thread " + this.getName() + " connecting to " + addr);
	if ((temp = addr.indexOf((char)(58))) != -1 ) {
	  port = Integer.parseInt(addr.substring(temp + 1));
	  addr = addr.substring(0, temp);
	} else
	  port = 411;
	
	// Connect
	Socket soc = new Socket();
	InetSocketAddress host = new InetSocketAddress(addr, port);
	if (host.isUnresolved())
	  throw new Error("Couldn't connect to " + addr + ":" + port + ": couln't resolve host", 1);	
	soc.connect(host, Integer.valueOf(Config.getProp("ConnectionTimeout")));
	soc.setSoTimeout(Integer.valueOf(Config.getProp("ConnectionTimeout")));
	in = new SeparatedBufferedReader(new InputStreamReader(soc.getInputStream()), "|");
	out = soc.getOutputStream();
	
	// Lock & Key
	strin = waitForBeginningString("$Lock", in);
	strin = strin.substring(strin.indexOf(' ') + 1, strin.lastIndexOf(' '));
	send("$Key " + decode(strin), out);

	// Nick
	int i = 0;
	do {
	  if (++i > Integer.valueOf(Config.getProp("nickTries")))
	    throw new Error(addr + ":" + port + " didn't accept any of the " + Config.getProp("nickTries") + " nicknames", 1);
	  nick = RandomStringUtils.randomAlphabetic(Integer.valueOf(Config.getProp("nickLength")));
	  nick = "[" + RandomStringUtils.randomAlphabetic(3) + "]" + nick;
	  send("$ValidateNick " + nick, out);
	} while ((strin = receive(in)).startsWith("$ValidateDenide") || strin.startsWith("$GetPass"));

	if (!strin.startsWith("$HubName"))
	  waitForBeginningString("$HubName", in);

	// Version
	waitForBeginningString("$Hello", in);
	send("$Version 1,0091", out);
	
	// MyInfo
	send("$MyINFO $ALL " + nick + " <++ V:0.4034,M:A,H:1/0/0,S:6>$ $Cable" + (byte)(001) + "$greger@holmstedt.ru$1125679625953$", out);
	
	// Search
 	send("$Search Hub:" + nick + " T?T?" + (size * 1000000) + "?1?" + file.replace(' ', '$') + "$$",out);
	Vector<Result> results = new Vector();
 	now = new Date().getTime();
 	while (new Date().getTime() - now < Integer.valueOf(Config.getProp("waitAfterSearch")) * 1000) {
 	  if ((strin = receive(in)).length() < 4)
 	    continue;
	  if (!strin.startsWith("$SR"))
	    continue;

	  System.out.println(strin+"\n");
	  // Parse result
	  Result res = new Result();
	  int first = strin.indexOf(" ");
	  int second = strin.indexOf(" ", first + 1);
	  int block = strin.indexOf("", second + 1);
	  int third = strin.indexOf(" ", block + 1);
	  int slash = strin.indexOf("/");
	  int block2 = strin.indexOf("", slash + 1);
	  res.nick = strin.substring(first + 1, second);
	  res.file = strin.substring(second + 1, block);
	  try {
	    res.loosers = Integer.parseInt(strin.substring(slash + 1, block2)) - Integer.parseInt(strin.substring(third + 1, slash));
	  } catch (NumberFormatException e) { System.out.println(first+":"+second+":"+block+":"+third+":"+slash+":"+block2); }
	  results.addElement(res);
	  System.out.println(res);
	}
	System.out.println(results.size());
	// Sort results
	if (results.size() > 0)
	  quicksort(results, 0, results.size() - 1);
	for (i = 0; i < results.size(); i++)
	  System.out.println("!!!!!" + results.elementAt(i));
	System.out.println(results.size());


       } catch (NumberFormatException e) {
 	Tankah.error(new Error("Couldn't connect to " + addr + ":" + port + ": invalid port", 1));
       } catch (IllegalArgumentException e) {
 	Tankah.error(new Error("Couldn't connect to " + addr + ":" + port + ": invalid port", 1));
       } catch (SocketTimeoutException e) {
 	Tankah.error(new Error("Couldn't connect to " + addr + ":" + port + ": connection timeout", 1));
       } catch (IOException e) {
 	Tankah.error(new Error("Couldn't connect to " + addr + ":" + port + ": connection refused", 1));
       } catch (Error e) {
 	Tankah.error(e);
       } catch (StringIndexOutOfBoundsException e) {
       } finally {
 	try {
 	  if (out != null)
	    try { send("$Quit", out); } catch (IOException e) {}
	  if (in != null)
	    in.close(); 
 	} catch (IOException e) {
 	  Tankah.error(new Error("Couln't close connection to " + addr + ":" + port + " (" + e.getMessage() + ")", 2));
 	  this.run();
 	}
       }
    }
  }

  private void quicksort (Vector<Result> a, int lo, int hi)
  {
    int i=lo, j=hi;
    int x=a.elementAt((lo+hi)/2).loosers;
    
    //  partition
    do
      {    
        while (a.elementAt(i).loosers<x) i++; 
        while (a.elementAt(j).loosers>x) j--;
        if (i<=j)
	  {
	  Result temp = a.elementAt(i);
	  a.set(i, a.elementAt(j));
	  a.set(j, temp);
	  i++; j--;
        }
    } while (i<=j);

    //  recursion
    if (lo<j) quicksort(a, lo, j);
    if (i<hi) quicksort(a, i, hi);
}



  private String waitForBeginningString(String str, BufferedReader in) throws Error, IOException {
    String temp;
    for (int i = 0; i < Integer.valueOf(Config.getProp("readTries")); i++) {
      temp = receive(in);
      if (temp.startsWith(str)) 
	return temp;
    }
    throw new Error("Couldn't get any understandable data", 2);
  }

  private String receive(BufferedReader in) throws Error, IOException {
    String retur = in.readLine();
    if (retur == null)
      throw new Error(addr + ":" + port + " suddenly closed the connection (probably fuckn Yhub-sheize)", 2);
    if (retur.startsWith("$OpForceMove") || retur.startsWith("$ForceMove") || retur.startsWith("$Kick"))
      throw new Error("Hub didn't let us in (" + (pre.startsWith("$") ? "no reason detected" : pre.substring(pre.indexOf(" ") + 1)) + ")", 1);
    if (retur.startsWith("$Error") || retur.startsWith("$Canceled"))
      throw new Error("An error occured, or the uploader canceled", 2);
    pre = retur;
    return retur;
  }
    

  private void send(String data, OutputStream out) throws IOException {
    out.write((data + "|").getBytes("ISO-8859-1"));
    out.flush();
  }

  private String decode(String validationString) {
        StringBuffer key = new StringBuffer();
        int i;
        int u,l,o;
        int v;
        int lockLength = validationString.length();

        /* first byte is a special case */
        u = (int) validationString.charAt(0);
        l = (int) validationString.charAt(lockLength - 1);
        o = (int) validationString.charAt(lockLength - 2);

        /* do xor */
        u = u ^ l ^ o ^ 0x05;		/* don't forget the magic 0x5 */

        /* do nibble swap */
        v = (((u << 8) | u) >> 4) & 255;
        key.append((char) v);

        /* all other bytes use the same code */
        for (i = 1; i < lockLength; i++) {
            u = (int) validationString.charAt(i);
            l = (int) validationString.charAt(i - 1);

            /* do xor */
            u = u ^ l;

            /* do nibble swap */
            v = (((u << 8) | u) >> 4) & 255;

            key.append((char) v);
        }
        return encodeValidationKey(key.toString());

    }

    /***
     Some characters are reserved, therefore we escape them using a wieard esceaping method.
     ***/

    private String encodeValidationKey(String key) {
        StringBuffer safeKey = new StringBuffer();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);


            if (c == 124)
                safeKey.append("/%DCN" + (int) c + "%/");
            else if (c == 36)
                safeKey.append("/%DCN0" + (int) c + "%/");
            else if (c == 126)
                safeKey.append("/%DCN" + (int) c + "%/");
            else if (c == 96)
                safeKey.append("/%DCN0" + (int) c + "%/");
            else if (c == 5)
                safeKey.append("/%DCN00" + (int) c + "%/");
            else if (c == 0)
                safeKey.append("/%DCN00" + (int) c + "%/");
            else
                safeKey.append(c);
        }
        return safeKey.toString();
    }
}

class Result {
  public String nick;
  public String file;
  public int loosers;

  public String toString() {
    return new String(nick + ":" + file + ":" + loosers);
  }
}

class HubList extends Thread {
  private static PrimitiveMap hublista = null;
  private static int nextHub;
  private static boolean available = true;
  private static HubList updater = new HubList();
  static {
    // Ladda in listan på hubadresser i hublist
    Tankah.debug("Loading hublist");
    InputStream in = null;
    SeparatedBufferedReader hubs = null;
    hublista = new PrimitiveMap();
    try {
      URL list = new URL(Config.getProp("hublist"));
      in = new URL(Config.getProp("hublist")).openStream();
      if (Config.getProp("hublist").substring(Config.getProp("hublist").lastIndexOf(".")).equals(".bz2")) { 
	try {
	  in = new CBZip2InputStream(in);
	} catch (NullPointerException e) { // There has to be a pretty way to do this instead of reconnecting, but I couldn't find it.. The first attempt to decompress the stream does something very, very weird to it. :-O
	  try {
	    if (in != null)
	      in.close();
	  } catch (IOException e2) {
	    throw new Error("Couldn't close old hublist-connection", 1);
	  }
	  in = new URL(Config.getProp("hublist")).openStream();
	  in.skip(2);
	  in = new CBZip2InputStream(in);
	}
      }
      hubs = new SeparatedBufferedReader(new InputStreamReader(in),"|||||");
      nextHub=1000;
      String hub;
      int first = 0;
      int second = 0;
      int third = 0;
      int current = 0;
      
      while ((hub = hubs.readLine()) != null) {
	try {
	  if ((first = hub.indexOf("|")) == -1 || (second = hub.indexOf("|", first + 1)) == -1 || (third = hub.lastIndexOf("|")) == -1)
	    continue;
	  
	  if ((current = Integer.parseInt(hub.substring(third + 1))) < Integer.valueOf(Config.getProp("minHubUsers")))
	    continue;
	  
	  for (; current != 0 && hublista.containsKey(current); current--) ;
	  
	  if (current  > nextHub)
	    nextHub = current; 
	  
	  hublista.put(current, hub.substring(first + 1, second));
	} catch (StringIndexOutOfBoundsException e) {
	  continue;
	}
      }
      
      
      // Starta en instans av oss själva som håller koll på nästa hub
      updater.start();
      
    } catch (Error e) {
      Tankah.error(e);
    } catch (NullPointerException e) {
      Tankah.error(new Error("Hublist is said to be in BZip2 format, but couldn't be decompressed. Please consider using another in "+Config.getFileName(), 3));
    } catch (MalformedURLException e) {
      Tankah.error(new Error("Invalid hublist adress, please check "+Config.getFileName(), 3));
    } catch (IOException e) {
      Tankah.error(new Error("Couldn't read hublist, please consider using another in "+Config.getFileName(), 3));
    } catch (NumberFormatException e) {
      Tankah.error(new Error("Couldn't determine minimum hubusers, please check "+Config.getFileName(), 3));
    } finally {
      try {
	if (hubs != null)
	  hubs.close();
      } catch (IOException e) {
	Tankah.error(new Error("Couln't close hublist-connection", 2));
      }
    }
  }
  
    public void run() {
      while (nextHub > 0) {
	while (available)
	  yield();
	
	for (;--nextHub != 0 && !hublista.containsKey(nextHub);) ;   
	available = true;
	
	yield();
      }
      Tankah.finito();
    }
  
  public static String getHub() {
    while (!available) {
      yield();
    }
    
    int temp = nextHub;
    available = false;
    return (String)hublista.get(temp);
  }
}

class PrimitiveMap extends HashMap {
  public PrimitiveMap() {
    super();
  }
  
  public void put(int i, Object o) {
    super.put(new Integer(i), o);
  }
  
  public Object get(int i) {
    return super.get(new Integer(i));
  }
  
  public boolean containsKey(int i) {
    return super.containsKey(new Integer(i));
  }
}

class SeparatedBufferedReader extends BufferedReader {  
  String separator;
  Reader in=null;

  public SeparatedBufferedReader(Reader in, String separator) {
    super(in);
    this.separator=separator;
    this.in=in;
  }
  
  public SeparatedBufferedReader(InputStreamReader in, String separator) {
    super(in);
    this.separator=separator;
    this.in=in;
  }
  
  public SeparatedBufferedReader(InputStreamReader in, int sz, String separator) {
    super(in,sz);
    this.separator=separator;
  }
  
  public SeparatedBufferedReader(Reader in, int sz, String separator) {
    super(in,sz);
    this.separator=separator;
  }
  
  public String readLine() throws IOException {
    // There is probably many more efficient ways to do this.
    String buffer=new String();
    int c;
  
    while ((c = read()) != -1) {
      buffer = buffer + (char)c;
      if (buffer.endsWith(separator))
	  return buffer.substring(0, buffer.length() - separator.length());
    }
    if (buffer.length() > 0)
      return buffer;
    else
      return null;


  }  
}

/**
 * Borrowed from Shasta Hub, http://shastahub.sourceforge.net
 * Please, feel free to hit me in the face.
 *
 * This converts a string provided in a $Lock command to the response
 * provided in the $Key command.  JavaDC and DCTC implement this.  I
 * just used the same algorithm.
 */
class LockDecode
{
  /**
   * Computes the $Key response to a given $Lock command.
   *
   * @param lock is the Lock string in the middle of "$Lock (.*) Pk=".
   * @param xorKey is 0x05 for a connection from a client to a hub,
   * or (localPort+(localPort>>8))&0xFF for a connection from a hub
   * to the hublist.
   */
  static String decode(String lock, byte xorKey)
    {
      StringBuffer key = new StringBuffer();

      int lockLength = lock.length();

      // The first byte is handled separately from the first, last,
      // and second to last bytes.
      int a1 = (int)lock.charAt(0);
      int a2 = (int)lock.charAt(lockLength - 1);
      int a3 = (int)lock.charAt(lockLength - 2);

      // Xor them all and swap nibbles.
      int char1 = a1 ^ a2 ^ a3 ^ xorKey;
      int char2 = ((char1<<4)&0xF0) | ((char1>>4)&0x0F);

      key.append((char)char2);
      
      // And do the rest of the bytes.
      for (int i = 1; i < lockLength; i++)
      {
	// Each key byte is the xor of the corresponding lock byte
	// and the one before it, nibble-swapped (that's why the 
	// first is special).
	int a5 = (int)lock.charAt(i);
	int a6 = (int)lock.charAt(i-1);
	
	int char3 = a5 ^ a6;
	int char4 = ((char3<<4)&0xF0) | ((char3>>4)&0x0F);

	key.append((char)char4);
      }

      // Key has the right bytes, but it needs to have certain
      // characters escaped.
      return escapeChars(key);
    }


  static String escapeChars(StringBuffer key)
    {
      StringBuffer encoded = new StringBuffer();

      for (int i = 0; i  < key.length(); i++)
      {
	// You might think we'd have to add the '/' escape character
	// to this list.  Oh well.
	int a = (int)key.charAt(i);
	if (a == 126 || // '~'
	    a == 124 || // '|'
	    a == 96 ||  // '`'
	    a == 36 ||  // '$'
	    a == 5 ||   // '^E'
	    a == 0)     // NUL
	{
	  encoded.append("/%DCN");
	  // Ensure we have 3 digits.
	  if (a < 100)
	    encoded.append("0");
	  if (a < 10)
	    encoded.append("0");
	  encoded.append(a); // As a string integer
	  encoded.append("%/");
	} else {
	  encoded.append((char)a);  // No transformation.
	}
      }

      return encoded.toString();
    }
}

