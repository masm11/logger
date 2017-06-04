/* Logger
    Copyright (C) 2016 Yuuki Harano

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package jp.ddo.masm11.logger;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.LinkedList;
import java.util.MissingFormatArgumentException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class Log {
    private static class Item {
	final int priority;
	final Throwable e;
	final String msg;
	final String klass;
	final String method;
	final Date stamp;
	Item(int priority, Throwable e, String msg,
		String klass, String method, Date stamp) {
	    this.priority = priority;
	    this.e = e;
	    this.msg = msg;
	    this.klass = klass;
	    this.method = method;
	    this.stamp = stamp;
	}
    }
    
    private static final char[] levels = new char[] { '?', '?', 'V', 'D', 'I', 'W', 'E', 'A', };
    private static Thread thread;
    private static boolean debugging;
    private static final LinkedList<Item> queue = new LinkedList<>();
    private static PrintWriter writer = null;
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static Method crashlyticsLogException = null;
    
    private static class Logger implements Runnable {
	public void run() {
	    try {
		while (true) {
		    Item item;
		    
		    synchronized (queue) {
			while (queue.size() == 0)
			    queue.wait();
			item = queue.remove();
		    }
		    
		    StringBuilder buf = new StringBuilder();
		    buf.append(item.method);
		    buf.append("(): ");
		    buf.append(item.msg);
		    if (item.e != null) {
			buf.append('\n');
			buf.append(android.util.Log.getStackTraceString(item.e));
		    }
		    String msg = buf.toString();
		    android.util.Log.println(item.priority, item.klass, msg);
		    
		    if (writer != null) {
			String time = formatter.format(item.stamp);
			writer.printf("%s %c/%s: %s\n", time, levels[item.priority], item.klass, msg);
			writer.flush();
		    }
		    
		    if (item.priority >= android.util.Log.ERROR) {
			if (crashlyticsLogException != null) {
			    try {
				crashlyticsLogException.invoke(null, item.e);
			    } catch (IllegalAccessException e) {
				android.util.Log.w("Log", "illegalaccessexception", e);
			    } catch (InvocationTargetException e) {
				android.util.Log.w("Log", "invocationtargetexception", e);
			    }
			}
		    }
		}
	    } catch (InterruptedException e) {
		android.util.Log.d("Logger", "interrupted", e);
	    }
	}
    }
    
    public static void init(File dir, boolean debugging) {
	if (thread != null)
	    return;
	
	Log.debugging = debugging;
	
	File logFile = new File(dir, "log.txt");
	
	try {
	    if (debugging && logFile.exists()) {
		writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
		writer.println("================");
		writer.flush();
	    }
	} catch (NullPointerException e) {
	    android.util.Log.e("Log", "nullpointerexception", e);
	} catch (IOException e) {
	    android.util.Log.e("Log", "ioexception", e);
	}
	
	thread = new Thread(new Logger());
	thread.start();
	
	try {
	    Class klass = Class.forName("com.crashlytics.android.Crashlytics");
	    crashlyticsLogException = klass.getMethod("logException", Throwable.class);
	} catch (ClassNotFoundException e) {
	    // android.util.Log.w("Log", "classnotfoundexception", e);
	} catch (NoSuchMethodException e) {
	    android.util.Log.w("Log", "nosuchmethodexception", e);
	}
    }
    
    public static void v(String msg, Throwable e) {
	common(android.util.Log.VERBOSE, msg, e);
    }
    
    public static void d(String msg, Throwable e) {
	common(android.util.Log.DEBUG, msg, e);
    }
    
    public static void i(String msg, Throwable e) {
	common(android.util.Log.INFO, msg, e);
    }
    
    public static void w(String msg, Throwable e) {
	common(android.util.Log.WARN, msg, e);
    }
    
    public static void e(String msg, Throwable e) {
	common(android.util.Log.ERROR, msg, e);
    }
    
    public static void wtf(String msg, Throwable e) {
	common(android.util.Log.ASSERT, msg, e);
    }
    
    public static void v(String msg) {
	common(android.util.Log.VERBOSE, msg, null);
    }
    
    public static void d(String msg) {
	common(android.util.Log.DEBUG, msg, null);
    }
    
    public static void i(String msg) {
	common(android.util.Log.INFO, msg, null);
    }
    
    public static void w(String msg) {
	common(android.util.Log.WARN, msg, null);
    }
    
    public static void e(String msg) {
	common(android.util.Log.ERROR, msg, null);
    }
    
    public static void wtf(String msg) {
	common(android.util.Log.ASSERT, msg, null);
    }
    
    private static void common(int priority, String msg, Throwable e) {
	if (!debugging && priority <= android.util.Log.DEBUG)
	    return;
	
	String[] stkinf = getStackInfo();
	Item item = new Item(priority, e, msg, stkinf[0], stkinf[1], new Date());
	synchronized (queue) {
	    queue.addLast(item);
	    queue.notify();
	}
    }
    
    private static String[] getStackInfo() {
	StackTraceElement[] elems = Thread.currentThread().getStackTrace();
	
	String className = elems[5].getClassName();
	int pos = className.lastIndexOf('.');
	if (pos >= 0)
	    className = className.substring(pos + 1);
	
	return new String[] { className, elems[5].getMethodName() };
    }
}
