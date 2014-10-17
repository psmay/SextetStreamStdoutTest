package us.hfgk.exp.sextetstream.demo;

import java.awt.BorderLayout;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class SextetStreamStdoutTest implements KeyListener, WindowListener {

    // This mapping is biased toward the US QWERTY keyboard
    private final int[] keycodesForStates = new int[]{
        KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9,
        KeyEvent.VK_ESCAPE, KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9,
        KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12, KeyEvent.VK_INSERT, KeyEvent.VK_HOME, KeyEvent.VK_PAGE_UP, KeyEvent.VK_DELETE, KeyEvent.VK_END, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_ENTER,
        KeyEvent.VK_Q, KeyEvent.VK_W, KeyEvent.VK_E, KeyEvent.VK_R, KeyEvent.VK_T, KeyEvent.VK_Y, KeyEvent.VK_U, KeyEvent.VK_I, KeyEvent.VK_O, KeyEvent.VK_P,
        KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_F, KeyEvent.VK_G, KeyEvent.VK_H, KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_L, KeyEvent.VK_SEMICOLON,
        KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_B, KeyEvent.VK_N, KeyEvent.VK_M, KeyEvent.VK_COMMA, KeyEvent.VK_SLASH, KeyEvent.VK_TAB,
        KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT
    };

    private final boolean[] states = new boolean[keycodesForStates.length];

    private final int SEXTETS_NEEDED_FOR_STATE = ((states.length + 5) / 6);

    private int extractSixStates(int startIndex) {
        int result = 0;
        int i, ai;

        for (i = 0; i < 6; ++i) {
            ai = startIndex + i;

            if (ai >= states.length) {
                break;
            }

            if (states[ai]) {
                result |= (1 << i);
            }
        }

        return result;
    }

    private byte printableSextet(int sextet) {
        return (byte) (((sextet + 0x10) & 0x3F) + 0x30);
    }

    private byte[] packCurrentStatePlusLF() {
        byte[] result = new byte[SEXTETS_NEEDED_FOR_STATE + 1];
        for (int i = 0; i < SEXTETS_NEEDED_FOR_STATE; ++i) {
            result[i] = printableSextet(extractSixStates(i * 6));
        }
        result[result.length - 1] = 0xA;
        return result;
    }

    @Override
    public void windowOpened(WindowEvent we) {
    }

    @Override
    public void windowClosing(WindowEvent we) {
        shutdown();
    }

    @Override
    public void windowClosed(WindowEvent we) {
    }

    @Override
    public void windowIconified(WindowEvent we) {
    }

    @Override
    public void windowDeiconified(WindowEvent we) {
    }

    @Override
    public void windowActivated(WindowEvent we) {
    }

    @Override
    public void windowDeactivated(WindowEvent we) {
    }

    @Override
    public void keyTyped(KeyEvent ke) {
        // ignored
    }

    @Override
    public void keyPressed(KeyEvent ke) {
        updateState(ke, true);
    }

    @Override
    public void keyReleased(KeyEvent ke) {
        updateState(ke, false);
    }

    private int lookupStateIndex(int keycode) {
        int i, len = keycodesForStates.length;
        for (i = 0; i < len; ++i) {
            if (keycodesForStates[i] == keycode) {
                return i;
            }
        }
        return -1;
    }

    private void updateState(KeyEvent ke, boolean newValue) {
        int stateIndex = lookupStateIndex(ke.getKeyCode());
        if (stateIndex >= 0 && states[stateIndex] != newValue) {
            states[stateIndex] = newValue;
            onChange();
        }
    }

    private volatile long lastOutputStateTime = 0;

    private synchronized void outputState(boolean automatic, long skipIfLastOutputAfter) {
        if (!(automatic && (lastOutputStateTime > skipIfLastOutputAfter))) {
            lastOutputStateTime = System.currentTimeMillis();

            byte[] outputData = packCurrentStatePlusLF();

            try {
                System.out.write(outputData);
                System.out.flush();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void outputState() {
        outputState(false, 0);
    }

    private void outputState(long skipIfLastOutputAfter) {
        outputState(true, skipIfLastOutputAfter);
    }

    private void onChange() {
        updateLabel();
        outputState();
    }

    private void updateLabel() {
        String on = "State:";
        for (int i = 0; i < states.length; ++i) {
            if (states[i]) {
                on += " " + i;
            }
        }
        label.setText(on);
    }

    private static class IdleRepeat implements Runnable {

        SextetStreamStdoutTest host;
        boolean running = true;
        final long interval = 1000;

        public IdleRepeat(SextetStreamStdoutTest host) {
            this.host = host;
        }

        @Override
        public void run() {
            long deadline = 0;
            while (running) {
                long now = System.currentTimeMillis();
                if (now >= deadline) {
                    host.outputState(deadline);
                    deadline = host.lastOutputStateTime + interval;
                } else {
                    try {
                        Thread.sleep(deadline - now);
                    } catch (InterruptedException iex) {
                        // OK to continue
                        // (running will be false if we're meant to stop)
                    }
                }
            }
        }
    }

    private static class Panel extends JPanel {

        public Panel(LayoutManager lm, SextetStreamStdoutTest host) {
            super(lm);
            init(host);
        }

        private void init(SextetStreamStdoutTest host) {
            addKeyListener(host);
            setFocusable(true);
        }

    }

    private final JFrame frame;
    private final Panel panel;
    private final JLabel label;
    private final IdleRepeat ir;
    private final Thread irt;

    public SextetStreamStdoutTest() {
        frame = new JFrame("SextetStream Stdout Test");
        frame.addWindowListener(this);

        panel = new Panel(new BorderLayout(), this);
        label = new JLabel("Press keys to change the state (note: numbering is arbitrary).");
        label.setHorizontalAlignment(JLabel.LEFT);
        label.setVerticalAlignment(JLabel.TOP);
        panel.add(label);
        frame.add(panel);
        frame.setSize(400, 100);
        frame.setVisible(true);
        ir = new IdleRepeat(this);
        irt = new Thread(ir);
    }

    private void go() {
        irt.start();
    }

    private void shutdown() {
        ir.running = false;
        irt.interrupt();
        while (true) {
            try {
                irt.join();
                break;
            } catch (InterruptedException e) {
                //continue;
            }
        }
        frame.dispose();
    }

    public static void main(String[] args) {
        (new SextetStreamStdoutTest()).go();
    }
}
