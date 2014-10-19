package us.hfgk.exp.sextetstream.demo;

import java.awt.BorderLayout;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class SextetStreamStdoutTest implements KeyListener, WindowListener {

    private final boolean[] states = new boolean[65536];

	private static final int sextetsNeeded(int bitCount) {
		return ((bitCount + 5) / 6);
	}

    private final int SEXTETS_NEEDED_FOR_STATE = sextetsNeeded(states.length + 5);

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

	private int currentStateBitsCount()
	{
		int lastTrueIndex;
		for(lastTrueIndex = states.length - 1; lastTrueIndex >= 0; --lastTrueIndex) {
			if(states[lastTrueIndex]) {
				break;
			}
		}
		return lastTrueIndex + 1;
	}

	private int currentOutputSextetsCount()
	{
		// Output must be non-empty.
		int bitCount = Math.max(1, currentStateBitsCount());
		return sextetsNeeded(bitCount);
	}

    private byte[] packCurrentStatePlusLF() {
		int sextetCount = currentOutputSextetsCount();
        byte[] buffer = new byte[sextetCount + 1];
        for (int i = 0; i < sextetCount; ++i) {
			int sextet = extractSixStates(i * 6);
            buffer[i] = printableSextet(sextet);
        }
        buffer[buffer.length - 1] = 0xA;
        return buffer;
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

    private void updateState(KeyEvent ke, boolean newValue) {
        int stateIndex = ke.getKeyCode();
        if (stateIndex >= 0 && stateIndex <= states.length && states[stateIndex] != newValue) {
            states[stateIndex] = newValue;
            onChange();
        }
    }

    private volatile long lastOutputStateTime = 0;

	// Can be called by either the main thread (the key event thread) or the watchdog thread (the thread that forces
	// output after a period of inactivity), so this is synchronized.
	//
	// If fromWatchdog is true, the actual last recorded output must be older than the deadline being handled by the
	// watchdog loop. If it is newer, that means a key event was handled before the deadline, so the extra output is
	// suppressed.
    private synchronized void outputState(boolean fromWatchdog, long skipIfLastOutputAfter) {
        if (!(fromWatchdog && (lastOutputStateTime > skipIfLastOutputAfter))) {
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

	// Called by key event thread
    private void outputState() {
        outputState(false, 0);
    }

	// Called by watchdog thread
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

    private static class RepeatWatchdog implements Runnable {

        SextetStreamStdoutTest host;
        boolean running = true;
        final long interval = 1000;

        public RepeatWatchdog(SextetStreamStdoutTest host) {
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
    private final RepeatWatchdog rwd;
    private final Thread rwdt;

    public SextetStreamStdoutTest() {
        frame = new JFrame("SextetStream Stdout Test");
        frame.addWindowListener(this);

        panel = new Panel(new BorderLayout(), this);
        label = new JLabel("Press keys to change the state (numbering may be arbitrary).");
        label.setHorizontalAlignment(JLabel.LEFT);
        label.setVerticalAlignment(JLabel.TOP);
        panel.add(label);
        frame.add(panel);
        frame.setSize(400, 100);
        frame.setVisible(true);
        rwd = new RepeatWatchdog(this);
        rwdt = new Thread(rwd);
    }

    private void go() {
        rwdt.start();
    }

    private void shutdown() {
        rwd.running = false;
        rwdt.interrupt();
        while (true) {
            try {
                rwdt.join();
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
