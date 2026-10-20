package pathivu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/** A table model for any list of objects: give it column names and how to read each column. */
class RowsModel<T> extends AbstractTableModel {
    private final String[] names;
    private final List<Function<T, Object>> cols;
    private List<T> rows = new ArrayList<>();

    @SafeVarargs
    RowsModel(String[] names, Function<T, Object>... cols) {
        this.names = names;
        this.cols = List.of(cols);
    }

    void setRows(List<T> rows) { this.rows = new ArrayList<>(rows); fireTableDataChanged(); }
    T row(int modelIndex) { return rows.get(modelIndex); }
    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return names.length; }
    @Override public String getColumnName(int c) { return names[c]; }
    @Override public Object getValueAt(int r, int c) { return cols.get(c).apply(rows.get(r)); }
}
