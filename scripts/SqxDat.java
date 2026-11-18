import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Lector minimo de ficheros OHLC .dat de SQX (formato nuevo). */
public class SqxDat {
  public long[] t; public double[] o,h,l,c; public int n;
  public static SqxDat read(String path, int decimals) throws Exception {
    byte[] all = Files.readAllBytes(Paths.get(path));
    ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.BIG_ENDIAN);
    int start = -1;
    outer: for (int i=0;i<Math.min(all.length-15,4096);i++){ for(int j=0;j<15;j++) if(all[i+j]!=j) continue outer; start=i; break; }
    if (start<0) throw new Exception("no magic");
    b.position(start);
    double dc = Math.pow(10, decimals);
    int cap = 1<<20; SqxDat d = new SqxDat();
    d.t=new long[cap]; d.o=new double[cap]; d.h=new double[cap]; d.l=new double[cap]; d.c=new double[cap];
    long pT=0,pO=0,pH=0,pL=0,pC=0,pV=0; long cnt=0;
    while (b.remaining() > 3) {
      if (cnt % 1000 == 0) { for(int j=0;j<15;j++){ if(b.get()!=j) throw new Exception("bad chain at rec "+cnt+" pos "+b.position()); } b.getInt(); }
      if (b.remaining() < 3) break;
      int c0=b.get()&0xff, c1=b.get()&0xff, c2=b.get()&0xff;
      int v3=c2&3, v4=(c2>>2)&3, v5=(c2>>4)&3, v6=(c2>>6)&3;
      int v7=c1&3, v8=(c1>>2)&3, v9=(c1>>4)&3, v10=(c1>>6)&3;
      int v11=c0&3, v12=(c0>>2)&3, v13=(c0>>4)&3, v14=(c0>>6)&3;
      pT=dec(b,pT,v13,v14); pO=dec(b,pO,v11,v12); pH=dec(b,pH,v9,v10); pL=dec(b,pL,v7,v8); pC=dec(b,pC,v5,v6); pV=dec(b,pV,v3,v4);
      if (d.n==d.t.length){ int nc=d.n*2; d.t=Arrays.copyOf(d.t,nc); d.o=Arrays.copyOf(d.o,nc); d.h=Arrays.copyOf(d.h,nc); d.l=Arrays.copyOf(d.l,nc); d.c=Arrays.copyOf(d.c,nc);}
      d.t[d.n]=pT; d.o[d.n]=pO/dc; d.h[d.n]=pH/dc; d.l[d.n]=pL/dc; d.c[d.n]=pC/dc; d.n++; cnt++;
    }
    return d;
  }
  static long val(ByteBuffer b,int dt) throws Exception { switch(dt){case 0:return b.get()&0xffL; case 1:return b.getShort()&0xffffL; case 2:return b.getInt()&0xffffffffL; case 3:return b.getLong();} throw new Exception("dt"); }
  static long dec(ByteBuffer b,long prev,int dt,int lt) throws Exception { long v=val(b,dt); switch(lt){case 0:return prev-v; case 1:return prev+v; case 2:return v;} throw new Exception("Unknown logic type "+lt+" at "+b.position()); }

  public static void main(String[] a) throws Exception {
    SqxDat d = read(a[0], Integer.parseInt(a[1]));
    java.time.LocalDateTime f=java.time.LocalDateTime.ofEpochSecond(d.t[0]/1000,0,java.time.ZoneOffset.UTC), l=java.time.LocalDateTime.ofEpochSecond(d.t[d.n-1]/1000,0,java.time.ZoneOffset.UTC);
    System.out.println("bars="+d.n+" first="+f+" last="+l);
    Set<String> days = new HashSet<>(Arrays.asList(a[2].split(",")));
    int from=Integer.parseInt(a[3]), to=Integer.parseInt(a[4]);
    for (int i=0;i<d.n;i++){ java.time.LocalDateTime t=java.time.LocalDateTime.ofEpochSecond(d.t[i]/1000,0,java.time.ZoneOffset.UTC); int hm=t.getHour()*100+t.getMinute();
      if (days.contains(t.toLocalDate().toString()) && hm>=from && hm<=to) System.out.printf("%s O=%.2f H=%.2f L=%.2f C=%.2f%n", t, d.o[i],d.h[i],d.l[i],d.c[i]); }
  }
}
