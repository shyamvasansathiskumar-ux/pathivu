package pathivu.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import pathivu.dao.Db;
import pathivu.service.*;

/** Writes the report for one incident to notes/sample-report-<ref>.txt */
public class SampleReport {
    public static void main(String[] a) throws Exception {
        Db db = Db.fromFile(Path.of("db.properties"), "db.");
        IncidentFile f = new IncidentService(db).load(Integer.parseInt(a[0]));
        Files.createDirectories(Path.of("notes"));
        Path out = Path.of("notes", "sample-report-" + f.incident().getRefCode() + ".txt");
        Files.writeString(out, new ReportService().build(f, new AuthService(db).names()));
        System.out.println(out);
    }
}
