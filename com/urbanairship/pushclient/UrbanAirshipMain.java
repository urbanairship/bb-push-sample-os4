package com.urbanairship.pushclient;

import net.rim.device.api.applicationcontrol.ApplicationPermissions;
import net.rim.device.api.applicationcontrol.ApplicationPermissionsManager;
import net.rim.device.api.system.ApplicationManager;
import net.rim.device.api.system.DeviceInfo;
import net.rim.device.api.system.GlobalEventListener;
import net.rim.device.api.ui.UiApplication;

public final class UrbanAirshipMain extends UiApplication implements GlobalEventListener {
	
	private static final String URBAN_AIRSHIP_MAIN 	= "UrbanAirshipMain";
	private final UrbanAirshipMain	_uiApp			= (UrbanAirshipMain)UiApplication.getUiApplication();
	private static final long GUID					= 0x30120912ea356c9cL;
	private static final long PUSH_GUID				= 0x8aba2a7ecd1ac66cL;

	private PushConnector pc						= null;
	
	private UAHomeScreen _hs						= null;

	private boolean acceptsForeground				= false;

	/**
	 * Starts a background process on device restart; otherwise, starts the event dispatcher and shows the home screen
	 * 
	 * @param args "autostartup" in args[0] kicks off background process.
	 */
	public static void main(String[] args) {
		
    	UrbanAirshipMain nd = new UrbanAirshipMain();
    	
        if( args.length > 0 && args[ 0 ].equals( "autostartup" ) )
    		{        	
        	// Register push application
        	nd.registerPushApplication();      
        	// Create background process on device restart, no UI
        	nd.enterEventDispatcher(); 
    		}
        else
    		{
        	// Display the User Interface on foreground starts
        	nd.showGUI();
    		}
   		}
	
	/**
	 * Shows the user interface and instantiates the event dispatcher.
	 */
    public void showGUI() {
    	// So we can see the app.
    	acceptsForeground = true;
    	_uiApp.requestForeground();
    	
    	String model = DeviceInfo.getDeviceName();
        Util.debugPrint(URBAN_AIRSHIP_MAIN, "Model: " + model);
        
        // So we can receive alerts from the notification thread
        addGlobalEventListener(this);

        // Register our PIN with Urban Airship
		Thread t0 = new Thread() {
			public void run() {
				if (UrbanAirshipStore.isPushEnabled().booleanValue()==true) {
					// Register our Device PIN with Urban Airship (without Alias)
					// UrbanAirshipAPI.urbanAirshipRegisterPIN();
					// Register our Device PIN with Urban Airship (with Alias)
					UrbanAirshipAPI.urbanAirshipRegisterPINWithAlias("My Device");
					}
				else {
					// Un-Register our Device PIN with Urban Airship 
					UrbanAirshipAPI.urbanAirshipUnRegisterPIN();
					}
		        }
			};
		t0.start();
		
		_uiApp._hs = new UAHomeScreen();
		UiApplication.getUiApplication().pushScreen(_uiApp._hs);
		
        // Prompt for app permissions
        promptPermissions();
		
		// Enter event dispatcher
        enterEventDispatcher();
		}
	
    /**
     * Handle our inbound notifications
     */
	void handleNotifications() {
		
		Runnable r = new Runnable() {
			public void run() {
        		String notification = UrbanAirshipStore.getNotification();
        		if (!notification.equalsIgnoreCase("")) {
        			final UrbanAirshipDialog uad = new UrbanAirshipDialog(notification);
    				try {
    			        Runnable t2 = new Runnable() {
    			        	public void run() {
    					        _uiApp.pushModalScreen(uad);
    			        		}
    			        	};
    			        	
    			        _uiApp.invokeLater(t2);
    					}
    				catch (IllegalStateException ex) {}
        			UrbanAirshipStore.setNotification("");
        			}
        		// Send event notification to turn off indicators
                ApplicationManager.getApplicationManager().postGlobalEvent(PUSH_GUID);
        		}
			};
		_uiApp.invokeLater(r);
		}
   
	/**
	 * Used to hide background process from application switcher.
	 */
    protected boolean acceptsForeground() {
    	return acceptsForeground;
    	}
    
   /**
   * Prompt for app permissions
   */
    private void promptPermissions() {
        ApplicationPermissionsManager apm 	= ApplicationPermissionsManager.getInstance();
        ApplicationPermissions ap 			= apm.getApplicationPermissions();
        
    	boolean permissionsOK = false;
        if (ap.getPermission(ApplicationPermissions.PERMISSION_FILE_API) ==
            ApplicationPermissions.VALUE_ALLOW
            &&
            ap.getPermission(ApplicationPermissions.PERMISSION_INTERNET) ==
            ApplicationPermissions.VALUE_ALLOW
            &&
            ap.getPermission(ApplicationPermissions.PERMISSION_CROSS_APPLICATION_COMMUNICATION) ==
            ApplicationPermissions.VALUE_ALLOW
            &&
            ap.getPermission(ApplicationPermissions.PERMISSION_INPUT_SIMULATION) ==
            ApplicationPermissions.VALUE_ALLOW
            &&
            ap.getPermission(ApplicationPermissions.PERMISSION_WIFI) ==
            ApplicationPermissions.VALUE_ALLOW) {
        	permissionsOK = true;
        } else {
            ap.addPermission(ApplicationPermissions.PERMISSION_FILE_API);
            ap.addPermission(ApplicationPermissions.PERMISSION_INTERNET);
            ap.addPermission(ApplicationPermissions.PERMISSION_WIFI);
            ap.addPermission(ApplicationPermissions.PERMISSION_INPUT_SIMULATION);
            ap.addPermission(ApplicationPermissions.PERMISSION_CROSS_APPLICATION_COMMUNICATION);
            
            permissionsOK = apm.invokePermissionsRequest(ap);
        	} 
        
        if (!permissionsOK) {
        	synchronized (getEventLock()) {
        		invokeLater(new Runnable() {
        		public void run() {
        			final UrbanAirshipDialog uad = new UrbanAirshipDialog("Insufficient Permissions to run Urban Airship Push Client... the application will now exit.");
    				try {
    			        Runnable t2 = new Runnable() {
    			        	public void run() {
    					        _uiApp.pushModalScreen(uad);
    			        		}
    			        	};
    			        	
    			        _uiApp.invokeLater(t2);
    					}
    				catch (IllegalStateException ex) {}
    				requestForeground();
        			}
        		});}
        	System.exit(0);
        	}
        else {
            }
        }
    
    /**
     * setStatusMessage Sets the status message on the Home Screen.
     * 
     * @param message the message to be displayed in the push status area.
     */
    public void setStatusMessage(final String message) {
		try {
	        Runnable t2 = new Runnable() {
	        	public void run() {
	        		if (_hs!=null && message!=null) {
	        			_uiApp._hs.lfPushStatus.setText(message);
	        			}
	        		}
	        	};
	        	
	        _uiApp.invokeLater(t2);
			}
		catch (NullPointerException ex) {}
		catch (IllegalStateException ex) {}
    }
    
    /**
     * Register (Deregister) our app with the RIM Push Service
     */
    public void registerPushApplication() {
    	
    	if (pc==null) {
    		pc = new PushConnector();	
    		}
		
		if (UrbanAirshipStore.isPushEnabled().booleanValue()==true) {
			// Push is enabled... register with RIM
			// pc.registerForService(); 
	        ApplicationManager.getApplicationManager().postGlobalEvent(PushConnector.PUSH_ENABLE_GUID);
			}
		else {
			// pc.deRegisterForService();
	        ApplicationManager.getApplicationManager().postGlobalEvent(PushConnector.PUSH_DISABLE_GUID);
			} 
		}

	// To turn on/off indicator
	public void eventOccurred(long guid, int data0, int data1, final Object notification, Object object1) {
		// On Event
		if (guid==GUID) {
	        Runnable r = new Runnable() {
	        	public void run() {
	                
	    	        if (notification!=null) {
	    				final UrbanAirshipDialog uad = new UrbanAirshipDialog((String)notification);
	    				try {
	    			        Runnable t2 = new Runnable() {
	    			        	public void run() {
	    					        _uiApp.pushModalScreen(uad);
	    			        		}
	    			        	};
	    			        	
	    			        _uiApp.invokeLater(t2);
	    					}
	    				catch (IllegalStateException ex) {}
	    	        	// Clear the notification in the data store
	        			UrbanAirshipStore.setNotification("");
        				}
	    	        
	    	        // Send event notification to turn off indicators
	    	        ApplicationManager.getApplicationManager().postGlobalEvent(PUSH_GUID);
	        		}
	        	};
	        _uiApp.invokeLater(r);
			}
		}
}
