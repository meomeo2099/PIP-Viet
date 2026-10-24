package com.geely.pipinstaller;
import java.util.Locale;
/** Only the owner's Car packages and known signing identity can request mutations. */
final class DashboardCallerPolicy {
 static final String CERT="C8A2E9BCCF597C2FB6DC66BEE293FC13F2FC47EC77BC6B2B0D52C11F51192AB8";
 static boolean allowed(String pkg,String cert,boolean sameSignature){
  boolean known="vn.tht.ex2.car".equals(pkg)||"vn.tht.ex2.car.platformtest".equals(pkg);
  return known&&(sameSignature||(cert!=null&&CERT.equals(cert.toUpperCase(Locale.ROOT))));
 }
 static boolean request(String id,int command){return id!=null&&id.matches("[A-Za-z0-9_-]{8,80}")&&command>=1&&command<=3;}
 private DashboardCallerPolicy(){}
}
