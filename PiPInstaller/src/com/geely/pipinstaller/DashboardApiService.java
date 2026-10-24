package com.geely.pipinstaller;
import android.app.Service;import android.content.*;import android.content.pm.*;import android.os.*;import java.security.MessageDigest;
/** Messenger IPC. The OS supplies sendingUid; no caller package/UID is trusted from extras. */
public final class DashboardApiService extends Service {
 private final Handler handler=new Handler(Looper.getMainLooper(),this::handle);
 private final Messenger endpoint=new Messenger(handler);
 @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
 private boolean caller(int uid){
  if(uid<0)return false;PackageManager pm=getPackageManager();String[] packages=pm.getPackagesForUid(uid);if(packages==null)return false;
  for(String pkg:packages)try{PackageInfo info=pm.getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);Signature[] signatures=info.signingInfo==null?null:info.signingInfo.getApkContentsSigners();String cert=null;if(signatures!=null&&signatures.length==1){byte[] digest=MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());StringBuilder hex=new StringBuilder();for(byte value:digest)hex.append(String.format(java.util.Locale.ROOT,"%02X",value&255));cert=hex.toString();}if(DashboardCallerPolicy.allowed(pkg,cert,pm.checkSignatures(pkg,getPackageName())==PackageManager.SIGNATURE_MATCH))return true;}catch(Exception ignored){}
  return false;
 }
 private boolean handle(Message message){
  if(message.replyTo==null)return true;
  if(!caller(message.sendingUid)){reply(message.replyTo,"",false,"Dashboard chưa có danh tính được cho phép");return true;}
  Bundle request;String id;try{request=message.getData();id=request.getString("request","");}catch(RuntimeException malformed){reply(message.replyTo,"",false,"Yêu cầu PIP không hợp lệ");return true;}
  if(!DashboardCallerPolicy.request(id,message.what)){reply(message.replyTo,id,false,"Yêu cầu PIP không hợp lệ");return true;}
  SharedPreferences prefs=PipService.state(this);boolean mutate=message.what==2||message.what==3;
  if(mutate&&(prefs.getBoolean("busy",false)||prefs.getBoolean("pending",false))){reply(message.replyTo,id,false,"PIP Việt đang xử lý thao tác khác");return true;}
  if(message.what==2&&prefs.getBoolean("rollback",false)){reply(message.replyTo,id,false,"Khôi phục thao tác trước rồi mới bật PIP");return true;}
  boolean launch=mutate||(message.what==1&&request.getBoolean("refresh",false)&&!prefs.getBoolean("pending",false));
  if(launch){
   if(!prefs.edit().putBoolean("busy",true).commit()){prefs.edit().putBoolean("busy",false).apply();reply(message.replyTo,id,false,"Không lưu được trạng thái chờ · chưa gửi lệnh");return true;}
   try{PipService.launch(this,message.what==2?PipService.ENABLE:message.what==3?PipService.DISABLE:PipService.REFRESH);}
   catch(RuntimeException denied){prefs.edit().putBoolean("busy",false).putString("message","Android từ chối khởi động PIP Việt").commit();reply(message.replyTo,id,false,"Android từ chối khởi động PIP Việt");return true;}
  }
  reply(message.replyTo,id,true,null);return true;
 }
 private void reply(Messenger target,String id,boolean accepted,String error){
  SharedPreferences prefs=PipService.state(this);Bundle state=new Bundle();state.putInt("api",1);state.putString("request",id);state.putBoolean("accepted",accepted);
  if(accepted){
   for(String key:new String[]{"busy","pending","rollback","enabled","compatible","privileged"})state.putBoolean(key,prefs.getBoolean(key,false));
   if(!state.getBoolean("busy")&&!state.getBoolean("pending"))try{state.putBoolean("enabled",new PlatformBridge(this).enabled());state.putBoolean("verified",true);}catch(Exception denied){state.putBoolean("accepted",false);error="Chưa đọc lại được PIP: "+denied.getMessage();}
  }
  state.putString("message",error!=null?error:prefs.getString("message","Đang đọc PIP Việt"));Message result=Message.obtain(null,1);result.setData(state);try{target.send(result);}catch(RemoteException disconnected){}
 }
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
