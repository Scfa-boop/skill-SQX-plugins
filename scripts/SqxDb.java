import java.sql.*;

/**
 * Lee (solo lectura) la base de datos SQLite de SQX (user/data/data.db): instrumentos, fuentes de datos, etc.
 * Uso: java -cp ".;<SQX>/internal/libs/sqlite-jdbc.jar" SqxDb <data.db> tables
 *      java -cp ... SqxDb <data.db> dump <tabla> [filtro LIKE sobre todas las columnas]
 *      java -cp ... SqxDb <data.db> sql "<SELECT ...>"
 * Trabajar sobre una COPIA de data.db con SQX cerrado si se va a modificar algo.
 */
public class SqxDb {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:file:" + a[0].replace('\\', '/') + "?mode=ro", new java.util.Properties())) {
      switch (a[1]) {
        case "tables": {
          try (ResultSet r = c.createStatement().executeQuery("select name, sql from sqlite_master where type='table' order by name")) {
            while (r.next()) {
              String t = r.getString(1); long n;
              try (ResultSet q = c.createStatement().executeQuery("select count(*) from \"" + t + "\"")) { n = q.next() ? q.getLong(1) : 0; }
              System.out.println("== " + t + " (" + n + " filas)\n" + r.getString(2));
            }
          }
          break;
        }
        case "dump": {
          String like = a.length > 3 ? a[3] : null;
          try (ResultSet r = c.createStatement().executeQuery("select * from \"" + a[2] + "\"")) { print(r, like); }
          break;
        }
        case "sql": try (ResultSet r = c.createStatement().executeQuery(a[2])) { print(r, null); } break;
      }
    }
  }
  static void print(ResultSet r, String like) throws SQLException {
    ResultSetMetaData m = r.getMetaData(); int n = m.getColumnCount();
    StringBuilder h = new StringBuilder(); for (int i = 1; i <= n; i++) h.append(i > 1 ? " | " : "").append(m.getColumnName(i));
    System.out.println(h);
    while (r.next()) {
      StringBuilder s = new StringBuilder();
      for (int i = 1; i <= n; i++) { Object o = r.getObject(i); String v = o instanceof byte[] ? "<blob " + ((byte[]) o).length + "B>" : String.valueOf(o); s.append(i > 1 ? " | " : "").append(v); }
      if (like == null || s.toString().toLowerCase().contains(like.toLowerCase())) System.out.println(s);
    }
  }
}
