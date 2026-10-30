import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** UI regression: model indices stay stable while evidence becomes immediately visible. */
public final class LicenseRecoverBatchEvidenceTableTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            try { inspectTable(args); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
        System.out.println("PASS: evidence columns, sorted rows, tooltips and separate evidence colors");
    }

    private static void inspectTable(String[] args) throws Exception {
        Object gui = LicenseRecoverModernGUI.class.getDeclaredConstructor().newInstance();
        Method create = LicenseRecoverModernGUI.class.getDeclaredMethod("buildBatchPanel");
        create.setAccessible(true);
        JPanel panel = (JPanel) create.invoke(gui);
        Field field = LicenseRecoverModernGUI.class.getDeclaredField("batchTable");
        field.setAccessible(true);
        JTable table = (JTable) field.get(gui);
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        check(table.convertColumnIndexToModel(2) == 8, "authorization state must immediately follow application");
        check(table.convertColumnIndexToModel(3) == 9, "detection reason must immediately follow authorization state");
        check("授权状态".equals(table.getColumnName(2)) && "检测原因".equals(table.getColumnName(3)),
                "visible column headers must describe evidence");
        check("授权状态".equals(model.getColumnName(8)) && "检测原因".equals(model.getColumnName(9)),
                "inspection model indices must remain compatible");
        check(model.getColumnClass(0) == Boolean.class && model.isCellEditable(0, 0)
                        && !model.isCellEditable(0, 8), "recovery checkbox edit contract remains unchanged");
        String[] statuses = {
            LicenseRecoverLicenseState.nativeVerification(true, "synthetic native result").display(),
            LicenseRecoverLicenseState.authorizationAcceptance("synthetic audited current predicate").display(),
            LicenseRecoverLicenseState.codeComparison(true, "synthetic reviewed pair").display(),
            LicenseRecoverLicenseState.nativeVerification(false, "synthetic native invalid").display(),
            LicenseRecoverLicenseState.unverified("configuration exists").display(),
            LicenseRecoverLicenseState.scanError("synthetic read error").display()
        };
        String[] names = {"Z-native", "A-entry", "B-patch", "C-invalid", "D-unknown", "E-error"};
        for (int i = 0; i < statuses.length; i++) {
            Object[] row = new Object[12];
            java.util.Arrays.fill(row, "—");
            row[0] = Boolean.FALSE; row[1] = names[i]; row[8] = statuses[i];
            row[9] = "synthetic-current-evidence-" + i;
            model.addRow(row);
        }
        table.getRowSorter().setSortKeys(Collections.singletonList(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
        table.setSize(1120, 300);
        table.doLayout();
        Color nativeGreen = color(table, 0, 2);
        Color acceptedBlue = color(table, 1, 2);
        Color patchGreen = color(table, 2, 2);
        Color unknownGray = color(table, 4, 2);
        Color errorRed = color(table, 5, 2);
        check(new Color(22, 94, 58).equals(nativeGreen), "verified native valid must be dark green");
        check(new Color(205, 239, 210).equals(patchGreen), "reviewed Patch must be light green");
        check(new Color(220, 235, 252).equals(acceptedBlue), "independent acceptance must be blue");
        check(!nativeGreen.equals(patchGreen) && !nativeGreen.equals(acceptedBlue)
                        && !patchGreen.equals(acceptedBlue), "different evidence must not share one green");
        check(Color.WHITE.equals(color(table, 3, 2)), "verified native invalid remains white");
        check(new Color(241, 243, 246).equals(unknownGray), "unverified must be neutral gray");
        check(new Color(165, 45, 45).equals(errorRed), "scan errors must be red");
        check(acceptedBlue.equals(color(table, 1, 0)), "checkbox uses the sorted row's evidence color");
        int acceptedViewRow = table.convertRowIndexToView(1);
        table.setRowSelectionInterval(acceptedViewRow, acceptedViewRow);
        check(acceptedBlue.equals(color(table, 1, 2)), "selection must not conceal evidence colors");
        java.awt.Rectangle cell = table.getCellRect(acceptedViewRow, 2, true);
        check(cell.x < 250, "authorization state starts inside the first viewport");
        String tooltip = table.getToolTipText(new MouseEvent(table, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, cell.x + 2, cell.y + 2, 0, false));
        check(tooltip.contains("当前授权入口判定通过") && tooltip.contains("不证明原生许可证有效")
                        && tooltip.contains("synthetic-current-evidence-1"),
                "sorted acceptance tooltip must include precise scope and current detection reason");
        if (args.length == 1) {
            // A real displayed JTable installs its header from addNotify(). This
            // offscreen preview has no peer, so attach that same header explicitly.
            ((JScrollPane) table.getParent().getParent()).setColumnHeaderView(table.getTableHeader());
            panel.setSize(1120, 720); layout(panel);
            BufferedImage image = new BufferedImage(1120, 720, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { panel.printAll(graphics); } finally { graphics.dispose(); }
            ImageIO.write(image, "png", new File(args[0]));
        }
    }

    private static Color color(JTable table, int modelRow, int viewColumn) {
        int viewRow = table.convertRowIndexToView(modelRow);
        Component cell = table.prepareRenderer(table.getCellRenderer(viewRow, viewColumn), viewRow, viewColumn);
        return cell.getBackground();
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }
}
