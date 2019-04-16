package org.bsc.cordova;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.webkit.ValueCallback;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author nagygyula21
 * forked from carlvorster/cordova-broadcaster
 */
public class CDVBroadcaster extends CordovaPlugin {

    // Log tag
    private static String LOGTAG = CDVBroadcaster.class.getSimpleName();
    // event name error string
    public static final String EVENTNAME_ERROR = "--- ERROR --- EVENT NAME NULL OR EMPTY!";
    // json object error string
    public static final String JSON_ERROR = "--- ERROR --- NOT VALID JSON OBJECT!";

    // Actions
    public static final String ACTION_NATIVE_EVENT = "fireNativeEvent";
    public static final String ACTION_ADD_EVENT_LISTENER = "addEventListener";
    public static final String ACTION_REMOVE_EVENT_LISTENER = "removeEventListener";

    // List of broadcast receivers
    java.util.Map<String, BroadcastReceiver> receiverMap = new java.util.HashMap<String, BroadcastReceiver>();

    /**
     * Javascript FireEvent
     * 
     * @param eventName    - Broadcast intent action name
     * @param jsonUserData - Broadcast extra json
     * @throws JSONException
     */
    protected void fireEvent(final String eventName, final Object jsonObject) throws JSONException {
        String method = null;

        // if valid json
        if (jsonObject != null) {
            final String data = String.valueOf(jsonObject);
            if (!(jsonObject instanceof JSONObject)) {
                // If not valid json object throw JSONException
                final JSONObject json = new JSONObject(data);
            }
            // with param
            method = String.format("window.broadcaster.fireEvent( '%s', %s );", eventName, data);
        } else {
            // without param
            method = String.format("window.broadcaster.fireEvent( '%s', {} );", eventName);
        }

        // send to browser
        sendJavascript(method);
    }

    /**
     * send to browser
     * @param javascript
     */
    private void sendJavascript(final String javascript) {
        webView.getView().post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.sendJavascript(javascript);
                } else {
                    webView.loadUrl("javascript:".concat(javascript));
                }
            }
        });
    }

    /**
     * Register a receiver
     * 
     * @param receiver BroadcastReceiver Object
     * @param filter   Intent filter
     */
    protected void registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
        ((CordovaActivity) this.cordova.getActivity()).registerReceiver(receiver, filter);
    }

    /**
     * Unregister a receiver
     * 
     * @param receiver BroadcastReceiver Object
     */
    protected void unregisterReceiver(android.content.BroadcastReceiver receiver) {
        ((CordovaActivity) this.cordova.getActivity()).unregisterReceiver(receiver);
    }

    /**
     * Send broadcast message to the ether
     * 
     * @param intent
     * @return
     */
    protected boolean sendBroadcast(android.content.Intent intent) {
        ((CordovaActivity) this.cordova.getActivity()).sendBroadcast(intent);
        return true;

    }

    @Override
    public Object onMessage(String id, Object data) {
        try {
            fireEvent(id, data);
        } catch (JSONException e) {
            Log.e(LOGTAG, String.format("userdata [%s] for event [%s] is not a valid json object!", data, id));
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    /**
     * To android
     * @param eventName
     * @param userData
     */
    private void fireNativeEvent(final String eventName, JSONObject jsonObject) {
        if (eventName == null) {
            throw new IllegalArgumentException(EVENTNAME_ERROR);
        }

        final Intent intent = new Intent(eventName);
        // put extra
        try {
            java.util.Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                
                // put
                if (value instanceof String) {
                    intent.putExtra(key, String.valueOf(value));
                }else if (value instanceof Integer) {
                    intent.putExtra(key, Integer.valueOf((String)value));
                }else if (value instanceof Float) {
                    intent.putExtra(key, Float.valueOf((String)value));
                }else if (value instanceof Boolean) {
                    intent.putExtra(key, ((Boolean)value).booleanValue());
                }
            }
        } catch (JSONException e) {
            Log.e(LOGTAG, JSON_ERROR);
        } catch (Exception ex) {
            Log.e(LOGTAG, "cannot be cast");
        }

        sendBroadcast(intent);
    }

    /**
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into
     *                        JavaScript.
     * @return
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // To android
        if (ACTION_NATIVE_EVENT.equals(action)) {
            // evemnt name
            final String eventName = args.getString(0);

            // not valid
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);

            }

            // from arg
            final JSONObject userData = args.getJSONObject(1);

            // thread
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    fireNativeEvent(eventName, userData);
                }
            });

            callbackContext.success();
            return true;

        } else if (ACTION_ADD_EVENT_LISTENER.equals(action)) {
            // Receive from android

            // Event name
            final String eventName = args.getString(0);

            // not valid event name
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            // If not exists event
            if (!receiverMap.containsKey(eventName)) {
                // create br
                final BroadcastReceiver r = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, final Intent intent) {
                        final Bundle b = intent.getExtras();
                        // parse the JSON passed as a string.
                        try {
                            JSONObject jsonObject = new JSONObject();
                            if (b != null) {
                                for (String key: b.keySet()) {
                                    Object value = b.get(key);
                                    jsonObject.put(key, value);
                                }
                            }
                            fireEvent(eventName, jsonObject);
                        } catch (JSONException e) {
                            Log.e(LOGTAG, JSON_ERROR);
                        }
                    }
                };

                // register
                registerReceiver(r, new IntentFilter(eventName));
                receiverMap.put(eventName, r);
            }

            callbackContext.success();
            return true;
        } else if (ACTION_REMOVE_EVENT_LISTENER.equals(action)) {
            // unregister android

            // Event name
            final String eventName = args.getString(0);

            // Not valid event
            if (eventName == null || eventName.isEmpty()) {
                callbackContext.error(EVENTNAME_ERROR);
                return false;
            }

            // remove map
            BroadcastReceiver r = receiverMap.remove(eventName);

            // Unregister br
            if (r != null) {
                unregisterReceiver(r);
            }

            callbackContext.success();
            return true;
        }
        return false;
    }

    /**
     * Unregister all broadcast receiver
     */
    @Override
    public void onDestroy() {
        for (BroadcastReceiver r : receiverMap.values()) {
            unregisterReceiver(r);
        }
        receiverMap.clear();
        super.onDestroy();
    }
}
