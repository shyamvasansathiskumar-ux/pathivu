package pathivu.service;

import java.util.List;
import pathivu.dao.AuditDao;
import pathivu.dao.Db;
import pathivu.model.AuditEvent;

public class AuditService {
    private final Db db;
    private final AuditDao audit = new AuditDao();
    public AuditService(Db db) { this.db = db; }
    public List<AuditEvent> all() { return db.read(c -> audit.list(c, null)); }
    public AuditDao.VerifyResult verify() { return db.read(audit::verify); }
    public String anchor() { return db.read(audit::anchor); }
    public AuditDao.VerifyResult verifyAnchor(String anchor) { return db.read(c -> audit.verifyAnchor(c, anchor)); }
}
