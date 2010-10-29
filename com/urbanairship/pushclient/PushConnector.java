/**
 * Derived from the RIM HttpPushDemo sample application.
 * 
 * Portions Copyright © 1998-2010 Research In Motion Ltd.
 */
package com.urbanairship.pushclient;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;

import net.rim.blackberry.api.messagelist.ApplicationIcon;
import net.rim.blackberry.api.messagelist.ApplicationIndicator;
import net.rim.blackberry.api.messagelist.ApplicationIndicatorRegistry;
import net.rim.device.api.io.http.HttpServerConnection;
import net.rim.device.api.io.http.MDSPushInputStream;
import net.rim.device.api.system.ApplicationManager;
import net.rim.device.api.system.Bitmap;
import net.rim.device.api.system.DeviceInfo;
import net.rim.device.api.system.EncodedImage;
import net.rim.device.api.system.GlobalEventListener;
import net.rim.device.api.system.LED;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.util.DataBuffer;

public class PushConnector implements GlobalEventListener {

	// Suffix to force using BIS servers when connecting, since the push API is
	// for BIS only
	private static final String CONN_SUFFIX 	= ";ConnectionType=mds-public;deviceside=false";
	private static final long GUID				= 0x30120912ea356c9cL;
	private static final long PUSH_GUID 		= 0x8aba2a7ecd1ac66cL;
	static final long PUSH_ENABLE_GUID 			= 0x8aba2a7ecd1ac66dL;
	static final long PUSH_DISABLE_GUID 		= 0x8aba2a7ecd1ac66eL;

	// Registration URL
	private static final String REGISTER_URL = Keys.BLACKBERRY_PUSH_URL
			+ "/mss/PD_subReg?serviceid=" + Keys.BLACKBERRY_PUSH_APPLICATION_ID + "&osversion="
			+ DeviceInfo.getSoftwareVersion() + "&model="
			+ DeviceInfo.getDeviceName();
	private static final String DEREGISTER_URL = Keys.BLACKBERRY_PUSH_URL
			+ "/mss/PD_subDereg?serviceid=" + Keys.BLACKBERRY_PUSH_APPLICATION_ID + "&osversion="
			+ DeviceInfo.getSoftwareVersion() + "&model="
			+ DeviceInfo.getDeviceName();
	private static final String BIS_PUSH_URL = "http://:" + Keys.BLACKBERRY_PUSH_PORT
			+ CONN_SUFFIX;

	private Listener _listener;
	private static final int CHUNK_SIZE = 256;

	/**
	 * Push Connector for OS 4.X.
	 */
	public PushConnector() {
        // Register our app indicator
        ApplicationIndicatorRegistry reg = ApplicationIndicatorRegistry.getInstance();
        EncodedImage mImage = EncodedImage.getEncodedImageResource("widdle_icon.png");
        ApplicationIcon mIcon = new ApplicationIcon(mImage);
        if (reg.getApplicationIndicator()==null) {
        	reg.register(mIcon, true, false);
        	}
    	
    	// Add a global listener so we can turn off indicators
        UiApplication.getUiApplication().addGlobalEventListener(this);
		}
	
	/**
	 * writeMessage write message to Console.
	 * 
	 * @param message Message to write to the Console.
	 */
	public void writeMessage(final String message) {
		UiApplication.getApplication().invokeLater(new Runnable() {
			public void run() {
				((UrbanAirshipMain)UiApplication.getUiApplication()).setStatusMessage(message);
				Util.debugPrint(getClass().getName() + " (OS 4.X)", message);
				}
			});
		}
	
	/**
	 * De-Register for Push Service with RIM.
	 * 
	 * @return true or false
	 */
	public boolean deRegisterForService() {
		writeMessage("De-Registering for push notifications with RIM");
		try {
			String token = new String(fetch(DEREGISTER_URL + CONN_SUFFIX));
			String verificationUrl = REGISTER_URL + "&" + token + CONN_SUFFIX;
			byte[] statusCodeData = fetch(verificationUrl);

			// Status code sent back to the application from the BB Push server
			final String statusCode = new String(statusCodeData);
			if (!statusCode.equals("rc=200") && !statusCode.equals("rc=10003")) {
				writeMessage("De-Registration failed with response code: "
						+ statusCode);
				return false;
			}
			writeMessage("RIM De-Registration succeeded.");
		} catch (IOException e) {
			writeMessage("RIM De-Registration failed with exception: " + e.toString());
			return false;
			}
		return true;
		}

	/**
	 * Register for Push Service with RIM.
	 * 
	 * @return true or false
	 */
	public boolean registerForService() {
		writeMessage("Registering for push notifications with RIM");
		try {
			String token = new String(fetch(REGISTER_URL + CONN_SUFFIX));
			String verificationUrl = REGISTER_URL + "&" + token + CONN_SUFFIX;
			byte[] statusCodeData = fetch(verificationUrl);

			// Status code sent back to the application from the BB Push server
			final String statusCode = new String(statusCodeData);
			if (!statusCode.equals("rc=200") && !statusCode.equals("rc=10003")) {
				writeMessage("RIM Registration failed with response code: "
						+ statusCode);
				return false;
			}
			writeMessage("RIM Registration succeeded.");
			_listener = new Listener();
			_listener.start();

		} catch (IOException e) {
			writeMessage("RIM Registration failed with exception: " + e.toString());
			return false;
		}
		return true;
	}

	private byte[] fetch(String url) throws IOException {
		HttpConnection httpConn = (HttpConnection) Connector.open(url);
		DataInputStream dis = httpConn.openDataInputStream();
		DataBuffer buffer = new DataBuffer();
		buffer.write(dis);
		return buffer.getArray();
		}

	private class Listener extends Thread {
		private boolean _stop = false;
		private StreamConnectionNotifier _notifier;

		public void run() {
			StreamConnection stream;
			while (!_stop) {
				try {
					// Synchronize here so that we don't end up creating a
					// connection that is never closed.
					synchronized (this) {
						// Open the connection once (or re-open after an
						// IOException), so we don't end up in a race condition,
						// where a push is lost if it comes in before the
						// connection is open again. We open the url with a
						// parameter that indicates that we should always use
						// MDS when attempting to connect.
						// writeMessage("Opening push connection");
						_notifier = (StreamConnectionNotifier) Connector
								.open(BIS_PUSH_URL);
					}

					while (!_stop) {
						stream = _notifier.acceptAndOpen(); // Blocking
						readAndShowData(stream);
					}

				} catch (IOException ioe) {
					// Likely the stream was closed. Catches the exception
					// thrown by _notify.acceptAndOpen() when this program exits.
					// writeMessage("Connection terminated");
				}

				if (_notifier != null) {
					try {
						_notifier.close();
						_notifier = null;
					} catch (IOException e) {
					}
				}
			}

		}

		/**
		 * Read and process notification message
		 * 
		 * @param stream inbound notification stream
		 */
		public void readAndShowData(StreamConnection stream) {
			InputStream input = null;
			MDSPushInputStream pushInputStream;

			try {
				input = stream.openInputStream();
				pushInputStream = new MDSPushInputStream(
						(HttpServerConnection) stream, input);

				// Extracts the data from the input stream.

				final DataBuffer buffer = new DataBuffer();
				byte[] data = new byte[CHUNK_SIZE];
				int chunk = 0;

				while (-1 != (chunk = input.read(data))) {
					buffer.write(data, 0, chunk);
				}

				// Signal that we have finished reading.
				pushInputStream.accept();
				
				// Turn on LED, set indicator, and save message for later review.
				handleMessage(new String(buffer.getArray()));
				
			} catch (IOException e1) {
				// A problem occurred with the input stream , however, the
				// original
				// StreamConnectionNotifier is still valid.
				// System.err.println(e1.toString());
				writeMessage("Read Error: restarting input stream");
				}

			// Close the input streams
			if (input != null) {
				try {
					input.close();
				} catch (IOException e2) {
				}
			}

			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e2) {
				}
			}
		}
	}
	
	/**
	 * Handle inbound notification messages
	 * 
	 * @param message Notification Message
	 */
	void handleMessage(String message) {

        try {
    		// Save the inbound message for later review in the data store
    		UrbanAirshipStore.setNotification(message);

    		// Turn on the LED
            LED.setConfiguration( 500, 250, LED.BRIGHTNESS_50 );
            LED.setState( LED.STATE_BLINKING );

            // Set the indicator on
	        ApplicationIndicatorRegistry reg = ApplicationIndicatorRegistry.getInstance();
	        ApplicationIndicator appIndicator = reg.getApplicationIndicator();
	        appIndicator.setVisible(true);
	        
	        Class cl = null;
			try {
				cl = Class.forName("com.urbanairship.pushclient.UrbanAirshipMain");
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				}
			if (cl!=null) {
				InputStream is = cl.getResourceAsStream("/cash.mp3");
				try {
					Player player = Manager.createPlayer(is, "audio/mpeg");
					player.realize();
					player.prefetch();
					player.start();
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (MediaException e) {
					e.printStackTrace();
					}
				}
			
			Bitmap bm = Bitmap.getBitmapResource("uaiconAlert.png");
			net.rim.blackberry.api.homescreen.HomeScreen.updateIcon(bm, 0);
	                    
            // And let's tell the app we have something for them, and turn on indicators
            ApplicationManager.getApplicationManager().postGlobalEvent(GUID, 0, 0, message, null);
        	}
        catch (IllegalArgumentException e) {
        	writeMessage("IllegalArgumentException: " + e.getMessage());
        	}
        catch (IllegalStateException e) {
        	writeMessage("IllegalStateException: " + e.getMessage());
        	}
	}

	// Turn off all indicators
	public void eventOccurred(long guid, int data0, int data1, Object object0, Object object1) {

		// Enable Push
		if (guid==PUSH_ENABLE_GUID) {
			registerForService();
			}
		
		// Disable Push
		if (guid==PUSH_DISABLE_GUID) {
			deRegisterForService();
			}
				
		// Off event
		if (guid==PUSH_GUID) {
    		// Turn off the LED
            LED.setState( LED.STATE_OFF );
            
	        try {
	            // Set the indicator on
		        ApplicationIndicatorRegistry reg = ApplicationIndicatorRegistry.getInstance();
		        ApplicationIndicator appIndicator = reg.getApplicationIndicator();
	        	appIndicator.setVisible(false);

	        	Bitmap bm = Bitmap.getBitmapResource("uaicon.png");
				net.rim.blackberry.api.homescreen.HomeScreen.updateIcon(bm, 0);
	        	}
	        catch (IllegalStateException e) {
	        	writeMessage("IllegalStateException: " + e.getMessage());
	        	}
			}
	}
}
