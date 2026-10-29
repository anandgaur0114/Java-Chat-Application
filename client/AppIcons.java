package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * AppIcons provides crisp, vector-rendered Swing Icons using pure Graphics2D.
 * Guarantees zero missing glyphs or square box ("tofu") artifacts across all Windows systems.
 */
public class AppIcons {

    public enum Type {
        MAIN_BOARD,
        DEPT_BOARD,
        LOCK,
        USER,
        FOLDER,
        ATTACHMENT,
        STATUS_DOT
    }

    public static class VectorIcon implements Icon {
        private final Type type;
        private final int size;
        private final Color primaryColor;
        private final Color secondaryColor;

        public VectorIcon(Type type, int size, Color primaryColor) {
            this(type, size, primaryColor, null);
        }

        public VectorIcon(Type type, int size, Color primaryColor, Color secondaryColor) {
            this.type = type;
            this.size = size;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = size;
            int h = size;

            switch (type) {
                case MAIN_BOARD: {
                    // Classical Institutional Pediment & Columns
                    g2.setColor(primaryColor);
                    // Pediment (Triangle Roof)
                    Polygon roof = new Polygon();
                    roof.addPoint(x + w / 2, y + 1);
                    roof.addPoint(x + 1, y + h / 3);
                    roof.addPoint(x + w - 1, y + h / 3);
                    g2.fillPolygon(roof);

                    // Entablature (Architrave Bar)
                    g2.fillRect(x + 2, y + h / 3 + 1, w - 4, 2);

                    // 3 Columns
                    int colWidth = Math.max(2, w / 7);
                    int colHeight = h / 2 - 2;
                    int colY = y + h / 3 + 4;
                    // Left column
                    g2.fillRect(x + 3, colY, colWidth, colHeight);
                    // Middle column
                    g2.fillRect(x + (w - colWidth) / 2, colY, colWidth, colHeight);
                    // Right column
                    g2.fillRect(x + w - 3 - colWidth, colY, colWidth, colHeight);

                    // Base Platform
                    g2.fillRect(x + 1, y + h - 3, w - 2, 3);
                    break;
                }

                case DEPT_BOARD: {
                    // Announcement Megaphone
                    g2.setColor(primaryColor);
                    // Horn
                    Polygon horn = new Polygon();
                    horn.addPoint(x + 3, y + h / 2 - 2);
                    horn.addPoint(x + 3, y + h / 2 + 2);
                    horn.addPoint(x + w - 4, y + h - 3);
                    horn.addPoint(x + w - 4, y + 3);
                    g2.fillPolygon(horn);

                    // Speaker Bell Rim
                    g2.drawRoundRect(x + w - 4, y + 3, 2, h - 6, 2, 2);

                    // Handle
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + w / 2 - 1, y + h / 2 + 2, x + w / 2 - 3, y + h - 2);

                    // Back button / mic
                    g2.fillRect(x + 1, y + h / 2 - 3, 2, 6);
                    break;
                }

                case LOCK: {
                    // Confidential Padlock
                    g2.setColor(primaryColor);
                    int bodyW = w - 4;
                    int bodyH = h / 2 + 1;
                    int bodyX = x + 2;
                    int bodyY = y + h / 2 - 1;

                    // Body
                    g2.fillRoundRect(bodyX, bodyY, bodyW, bodyH, 4, 4);

                    // Shackle (Arch)
                    int shackleW = w / 2;
                    int shackleX = x + (w - shackleW) / 2;
                    int shackleY = y + 1;
                    int shackleH = h / 2 + 1;
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawArc(shackleX, shackleY, shackleW, shackleH, 0, 180);

                    // Keyhole dot
                    g2.setColor(Color.WHITE);
                    int kw = Math.max(2, w / 6);
                    g2.fillOval(x + (w - kw) / 2, bodyY + bodyH / 3, kw, kw);
                    g2.fillRect(x + (w - 2) / 2, bodyY + bodyH / 3 + kw, 2, 3);
                    break;
                }

                case USER: {
                    // Person Avatar (Head + Torso)
                    g2.setColor(primaryColor);
                    int headSize = w / 2 - 1;
                    int headX = x + (w - headSize) / 2;
                    int headY = y + 1;
                    g2.fillOval(headX, headY, headSize, headSize);

                    // Torso (Arc/Pill)
                    int torsoW = w - 2;
                    int torsoH = h / 2 + 2;
                    int torsoX = x + 1;
                    int torsoY = y + h / 2 + 1;
                    g2.fillArc(torsoX, torsoY, torsoW, torsoH, 0, 180);
                    break;
                }

                case FOLDER: {
                    // Document Folder
                    g2.setColor(primaryColor);
                    // Tab
                    g2.fillRoundRect(x + 1, y + 2, w / 2, 4, 2, 2);
                    // Folder Body
                    g2.fillRoundRect(x + 1, y + 4, w - 2, h - 6, 4, 4);
                    // Accent line
                    if (secondaryColor != null) {
                        g2.setColor(secondaryColor);
                        g2.fillRect(x + 3, y + 8, w - 6, 2);
                    }
                    break;
                }

                case ATTACHMENT: {
                    // Paperclip
                    g2.setColor(primaryColor);
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int px = x + 3;
                    int py = y + 2;
                    int pw = w - 6;
                    int ph = h - 4;
                    // Draw outer loop and inner loop
                    g2.drawRoundRect(px, py, pw, ph, pw, pw);
                    g2.drawRoundRect(px + 2, py + 3, pw - 4, ph - 6, pw - 4, pw - 4);
                    break;
                }

                case STATUS_DOT: {
                    g2.setColor(primaryColor);
                    g2.fillOval(x + 2, y + 2, w - 4, h - 4);
                    break;
                }
            }

            g2.dispose();
        }
    }

    // Standard Icon Presets
    public static final Icon MAIN_BOARD_16 = new VectorIcon(Type.MAIN_BOARD, 16, new Color(30, 58, 138));
    public static final Icon MAIN_BOARD_20 = new VectorIcon(Type.MAIN_BOARD, 20, new Color(30, 58, 138));
    public static final Icon MAIN_BOARD_GOLD = new VectorIcon(Type.MAIN_BOARD, 16, new Color(217, 119, 6));

    public static final Icon DEPT_BOARD_16 = new VectorIcon(Type.DEPT_BOARD, 16, new Color(5, 150, 105));
    public static final Icon DEPT_BOARD_20 = new VectorIcon(Type.DEPT_BOARD, 20, new Color(5, 150, 105));

    public static final Icon LOCK_16 = new VectorIcon(Type.LOCK, 16, new Color(124, 58, 237));
    public static final Icon LOCK_20 = new VectorIcon(Type.LOCK, 20, new Color(124, 58, 237));

    public static final Icon USER_16 = new VectorIcon(Type.USER, 16, new Color(71, 85, 105));
    public static final Icon USER_20 = new VectorIcon(Type.USER, 20, new Color(30, 58, 138));

    public static final Icon FOLDER_16 = new VectorIcon(Type.FOLDER, 16, Color.WHITE, new Color(254, 240, 138));
    public static final Icon ATTACH_16 = new VectorIcon(Type.ATTACHMENT, 16, new Color(71, 85, 105));

    public static final Icon DOT_ONLINE = new VectorIcon(Type.STATUS_DOT, 10, new Color(16, 185, 129));
}
