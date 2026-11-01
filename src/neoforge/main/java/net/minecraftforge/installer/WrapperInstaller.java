package net.minecraftforge.installer;

import java.awt.*;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;
import java.awt.event.MouseAdapter;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.border.LineBorder;
import net.minecraftforge.installer.automodpack.Installation;

public final class WrapperInstaller {
    private static final Logger LOGGER = Logger.getLogger(WrapperInstaller.class.getName());
    private static final int HOVER_ANIMATION_DURATION = 180;
    private static final int HOVER_ANIMATION_INTERVAL = 15;

    enum Theme {
        LIGHT, DARK
    }

    enum LauncherApp {
        CURSEFORGE("CurseForge", "https://www.curseforge.com/download/app?clientType=standalone"),
        MODRINTH("Modrinth", "https://api.github.com/repos/modrinth/theseus/releases/latest");

        private final String displayName;
        private final String downloadUrl;

        LauncherApp(String displayName, String downloadUrl) {
            this.displayName = displayName;
            this.downloadUrl = downloadUrl;
        }
8
        String displayName() {
            return displayName;
        }

        String downloadUrl() {
            return downloadUrl;
        }
    }

    private WrapperInstaller() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to set system look and feel", e);
			}
		});
  if (args.length > 0) {
            runCommandLine(args);
        } else {
            SwingUtilities.invokeLater(() -> new WrapperInstaller().createAndShowGUI());
        }
    }

    private static void runCommandLine(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar installer.jar <version> <action> <directory>");
            System.err.println("Actions: --installClient, --installServer, --fat-installer");
            return;
        }
        try {
            String action = validateAction(args[0]);
            String version = validateVersion(args[1]);
            File target = validateTarget(args[2]);
            File installer = OfficialInstallerLauncher.download(version);
            int exitCode = runInstallation(installer, action, version, target);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Command-line installation failed", exception);
            System.exit(1);
        }
    }

    private static String validateAction(String action) {
        return switch (action) {
            case "--installClient" -> "--installClient";
            case "--installServer" -> "--installServer";
            case "--fat-installer" -> "--fat-installer";
            default -> throw new IllegalArgumentException("Unsupported installer action");
        };
    }

    private static String validateVersion(String version) {
        String installerUrl = NeoForgeVersionApi.installerUrl(version);
        String fileName = installerUrl.substring(installerUrl.lastIndexOf('/') + 1);
        return fileName.substring("neoforge-".length(), fileName.length() - "-installer.jar".length());
    }

    private static File validateTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Installation directory must not be blank");
        }
        return Path.of(target).toAbsolutePath().normalize().toFile();
    }

    private static int runInstallation(File installer, String action, String neoForgeVersion, File target)
            throws java.io.IOException, InterruptedException {
        return runInstallation(installer, action, neoForgeVersion, target, System.out::println);
    }

    private static int runInstallation(File installer, String action, String neoForgeVersion, File target, Consumer<String> output) throws java.io.IOException, InterruptedException {
        return runInstallation(installer, action, neoForgeVersion, target, null, output);
    }

    private static int runInstallation(File installer, String action, String neoForgeVersion, File target,
            Path javaExecutable, Consumer<String> output) throws java.io.IOException, InterruptedException {
        String safeAction = validateAction(action);
        String safeVersion = validateVersion(neoForgeVersion);
        File safeTarget = validateTarget(target.getPath());
        int exitCode = javaExecutable == null
                ? OfficialInstallerLauncher.run(installer, safeAction, safeTarget, output)
                : OfficialInstallerLauncher.run(installer, safeAction, safeTarget, javaExecutable, output);
        if (exitCode == 0 && installsGame(safeAction)) {
            output.accept("NeoForge installation finished. Installing AutoModpack...\n");
            Installation.installAutomodpackOrThrow(minecraftVersion(safeVersion), "neoforge",
                    safeTarget.toPath(), output);
            output.accept("NeoForge and AutoModpack installation completed successfully.\n");
        }
        return exitCode;
    }

   	static boolean installsGame(String action) {
		return "--installClient".equals(action) || "--installServer".equals(action);
	}

	static boolean hasMinecraftLauncherProfile(File target) {
		return target.isDirectory()
				&& (new File(target, "launcher_profiles.json").isFile()
				|| new File(target, "launcher_profiles_microsoft_store.json").isFile());
	}

	static String clientInstallPreflightError(File target) {
		if (!target.isDirectory()) {
			return "There is no minecraft installation at: " + target;
		}
		if (!hasMinecraftLauncherProfile(target)) {
			return "There is no minecraft launcher profile in \"" + target
					+ "\", you need to run the launcher first!";
		}
		return null;
	}

    static boolean shouldInstallNeoForge(boolean launcherProfile) {
        return !launcherProfile;
    }

    static String minecraftVersion(String neoForgeVersion) {
        String safeVersion = validateVersion(neoForgeVersion);
        int separator = safeVersion.indexOf('-');
        String minecraftVersion = separator >= 0 ? safeVersion.substring(0, separator) : safeVersion;
        String[] parts = minecraftVersion.split("\\.");
        if (separator >= 0 || parts.length <= 2) {
            return minecraftVersion;
        }
        return parts[0] + "." + parts[1];
    }

	public static void addComponentsToPane(Container pane) {
		pane.setLayout(new GridBagLayout());
		pane.add(new JLabel("Use the controls below to select a version and destination."),
				constraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL));
	}

	private static GridBagConstraints constraints(int x, int y, double weightX, double weightY, int fill) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.weightx = weightX;
		constraints.weighty = weightY;
		constraints.fill = fill;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.insets = new Insets(6, 8, 6, 8);
		return constraints;
	}

	private void createAndShowGUI() {
		applyThemeDefaults(Theme.LIGHT);
		JFrame frame = new JFrame("Installer");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(createInstallerPanel(frame));
		frame.setJMenuBar(createMenuBar(frame));
		applyComponentThemeRecursively(frame, Theme.LIGHT);
		frame.pack();
		frame.setMinimumSize(new Dimension(640, 460));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private JMenuBar createMenuBar(JFrame frame) {
		JMenuBar menuBar = new JMenuBar();
		menuBar.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic('F');
		configureTopLevelMenu(fileMenu);
		JMenuItem exit = new JMenuItem("Exit");
		exit.setAccelerator(KeyStroke.getKeyStroke("ctrl Q"));
		exit.addActionListener(event -> frame.dispose());
		fileMenu.add(exit);

		JMenu settingsMenu = new JMenu("Settings");
		settingsMenu.setMnemonic('S');
		configureTopLevelMenu(settingsMenu);
		JMenu appearanceMenu = new JMenu("Appearance");
		appearanceMenu.setHorizontalAlignment(SwingConstants.LEFT);
		ButtonGroup themes = new ButtonGroup();
		addThemeItem(appearanceMenu, themes, "Light", Theme.LIGHT, frame, true);
		addThemeItem(appearanceMenu, themes, "Dark", Theme.DARK, frame, false);
		settingsMenu.add(appearanceMenu);

		JMenu helpMenu = new JMenu("Help");
		configureTopLevelMenu(helpMenu);
		JMenuItem about = new JMenuItem("About");
		about.addActionListener(event -> JOptionPane.showMessageDialog(frame,
				"AutoModpack Installer\nInstalls AutoModpack and NeoForge when needed.",
				"About", JOptionPane.INFORMATION_MESSAGE));
		helpMenu.add(about);

		menuBar.add(fileMenu);
		menuBar.add(settingsMenu);
		menuBar.add(helpMenu);
		return menuBar;
	}

	private void configureTopLevelMenu(JMenu menu) {
		menu.setHorizontalAlignment(SwingConstants.CENTER);
		menu.setMargin(new Insets(2, 12, 2, 12));
	}

	private void addThemeItem(JMenu menu, ButtonGroup themes, String label, Theme theme,
			JFrame frame, boolean selected) {
		JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, selected);
		item.addActionListener(event -> applyTheme(frame, theme));
		themes.add(item);
		menu.add(item);
	}

	static String themeName(Theme theme) {
		return switch (theme) {
			case LIGHT -> "Light";
			case DARK -> "Dark";
		};
	}

	static String launcherAppUrl(LauncherApp app) {
		return app.downloadUrl();
	}

	private void installLauncherApp(JFrame frame, LauncherApp app,
			JTextArea status, JLabel completion) {
		status.append("Downloading the official standalone " + app.displayName() + " installer...\n");
		completion.setText("Downloading " + app.displayName() + " installer");
		new SwingWorker<Path, String>() {
			@Override
			protected Path doInBackground() throws Exception {
				return downloadLauncherInstaller(app, this::publish);
			}

			@Override
			protected void process(List<String> messages) {
				messages.forEach(message -> status.append(message + "\n"));
			}

			@Override
			protected void done() {
				try {
					Path installer = get();
					launchInstaller(installer);
					status.append("Installer started: " + installer + "\n");
					completion.setText(app.displayName() + " installer started");
					completion.setForeground(new Color(0, 128, 0));
				} catch (Exception exception) {
					Throwable cause = exception.getCause() == null ? exception : exception.getCause();
					String message = cause.getMessage() == null || cause.getMessage().isBlank()
							? cause.toString() : cause.getMessage();
					status.append("Could not install " + app.displayName() + ": " + message + "\n");
					completion.setText(app.displayName() + " installation failed");
					completion.setForeground(Color.RED);
					JOptionPane.showMessageDialog(frame, message,
							app.displayName() + " installation", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	static Path downloadLauncherInstaller(LauncherApp app, Consumer<String> output) throws IOException {
		String downloadUrl = app == LauncherApp.MODRINTH
				? resolveModrinthDownloadUrl() : app.downloadUrl();
		URL url = new URL(downloadUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestProperty("User-Agent", "AutoModpack-Installer");
		connection.setInstanceFollowRedirects(true);
		if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
			throw new IOException("Installer download returned HTTP " + connection.getResponseCode());
		}
		String contentType = connection.getContentType();
		if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
			throw new IOException("The official launcher endpoint returned a webpage instead of an installer");
		}
		String suffix = installerSuffix(connection.getContentType(), downloadUrl);
		Path target = Files.createTempFile("automodpack-" + app.displayName().toLowerCase(Locale.ROOT) + "-", suffix);
		try (InputStream input = connection.getInputStream()) {
			Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			connection.disconnect();
		}
		if (Files.size(target) == 0) {
			Files.deleteIfExists(target);
			throw new IOException("The downloaded installer was empty");
		}
		output.accept("Downloaded installer to " + target);
		return target;
	}

	private static String resolveModrinthDownloadUrl() throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(LauncherApp.MODRINTH.downloadUrl()).openConnection();
		connection.setRequestProperty("Accept", "application/vnd.github+json");
		connection.setRequestProperty("User-Agent", "AutoModpack-Installer");
		if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
			throw new IOException("Modrinth release lookup returned HTTP " + connection.getResponseCode());
		}
		try (InputStream input = connection.getInputStream()) {
			String json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			String preferred = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
					? "\\.exe" : System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "\\.dmg" : "\\.AppImage";
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+" + preferred + ")\\\"").matcher(json);
			if (matcher.find()) {
				return matcher.group(1).replace("\\u0026", "&");
			}
			throw new IOException("No compatible Modrinth installer was found in the latest release");
		} finally {
			connection.disconnect();
		}
	}

	static String installerSuffix(String contentType, String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		if (lower.contains(".dmg")) return ".dmg";
		if (lower.contains(".appimage")) return ".AppImage";
		if (lower.contains(".deb")) return ".deb";
		if (lower.contains(".rpm")) return ".rpm";
		return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("zip") ? ".zip" : ".exe";
	}

	private static void launchInstaller(Path installer) throws IOException {
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			new ProcessBuilder(installer.toString()).start();
		} else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Desktop.getDesktop().open(installer.toFile());
		} else {
			throw new IOException("This computer cannot start the downloaded installer automatically");
		}
	}

	private void applyTheme(JFrame frame, Theme theme) {
		try {
			applyThemeDefaults(theme);
			SwingUtilities.updateComponentTreeUI(frame);
			applyComponentThemeRecursively(frame, theme);
			frame.pack();
			frame.repaint();
		} catch (Exception exception) {
			LOGGER.log(Level.WARNING, "Failed to apply " + themeName(theme) + " theme", exception);
		}
	}

	static void applyComponentTheme(Component component, Theme theme) {
		applyThemeDefaults(theme);
		applyComponentThemeRecursively(component, theme);
	}

	private static void applyComponentThemeRecursively(Component component, Theme theme) {
		Color background = theme == Theme.LIGHT ? Color.WHITE : new Color(43, 43, 43);
		Color foreground = theme == Theme.LIGHT ? Color.BLACK : new Color(235, 235, 235);
		Color control = theme == Theme.LIGHT ? new Color(238, 238, 238) : new Color(65, 65, 65);
		if (component instanceof StatusBox statusBox) {
			statusBox.applyTheme(theme);
			return;
		} else if (component instanceof JMenuBar || component instanceof JMenu || component instanceof JMenuItem
				|| component instanceof JPopupMenu) {
			component.setBackground(background);
			component.setForeground(foreground);
		} else if (component instanceof JTextComponent) {
			component.setBackground(control);
			component.setForeground(foreground);
		} else if (component instanceof JComboBox<?> || component instanceof AbstractButton) {
			component.setBackground(control);
			component.setForeground(foreground);
		} else if (component instanceof JLabel || component instanceof JPanel
				|| component instanceof JViewport || component instanceof JScrollPane) {
			component.setBackground(background);
			component.setForeground(foreground);
		}
		if (component instanceof JComboBox<?> comboBox) {
			comboBox.setUI(new FlatComboBoxUI());
			comboBox.setBorder(BorderFactory.createCompoundBorder(
					new FocusAwareComboBoxBorder(theme, comboBox),
					BorderFactory.createEmptyBorder(4, 8, 4, 8)));
			installComboHoverFeedback(comboBox, control, UIManager.getColor("ComboBox.selectionBackground"));
		} else if (component instanceof JButton button) {
			button.setRolloverEnabled(true);
			if (!(button.getParent() instanceof JComboBox<?>)) {
				button.setUI(new RoundedButtonUI());
				button.setContentAreaFilled(false);
				button.setOpaque(false);
				button.setFocusPainted(false);
				button.setBorder(buttonBorder(theme));
			}
			installHoverFeedback(button, control, UIManager.getColor("Button.select"));
		} else if (component instanceof JTextField || component instanceof JScrollPane) {
			((JComponent) component).setBorder(flatControlBorder(theme));
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				applyComponentThemeRecursively(child, theme);
			}
		}
		if (component instanceof JComboBox<?> comboBox) {
			for (Component child : comboBox.getComponents()) {
				if (child instanceof AbstractButton button) {
					button.setBorder(BorderFactory.createEmptyBorder());
					installHoverFeedback(button, comboBox, UIManager.getColor("ComboBox.selectionBackground"));
				}
			}
		}
	}

	private static void installHoverFeedback(JComponent component, Color normal, Color hover) {
		if (hover == null) {
			return;
		}
		Object previous = component.getClientProperty("WrapperInstaller.hoverListener");
		if (previous instanceof java.awt.event.MouseListener listener) {
			component.removeMouseListener(listener);
		}
		java.awt.event.MouseListener listener = new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(java.awt.event.MouseEvent event) {
				animateHoverColor(component, normal, hover);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent event) {
				animateHoverColor(component, hover, normal);
			}
		};
		component.putClientProperty("WrapperInstaller.hoverListener", listener);
		component.addMouseListener(listener);
	}

	private static void animateHoverColor(JComponent component, Color start, Color target) {
		Object previous = component.getClientProperty("WrapperInstaller.hoverTimer");
		if (previous instanceof Timer timer) {
			timer.stop();
		}
		component.putClientProperty("WrapperInstaller.hoverTarget", target);
		Color current = component.getBackground();
		if (current == null) {
			current = start;
		}
		Color initial = current;
		long started = System.currentTimeMillis();
		Color initialColor = initial;
		Timer timer = new Timer(HOVER_ANIMATION_INTERVAL, event -> {
			float progress = Math.min(1f,
					(System.currentTimeMillis() - started) / (float) HOVER_ANIMATION_DURATION);
			component.setBackground(interpolateColor(initialColor, target, progress));
			component.repaint();
			if (progress >= 1f) {
				((Timer) event.getSource()).stop();
				component.putClientProperty("WrapperInstaller.hoverTimer", null);
			}
		});
		component.putClientProperty("WrapperInstaller.hoverTimer", timer);
		timer.start();
	}

	static Color interpolateColor(Color start, Color end, float progress) {
		float clamped = Math.max(0f, Math.min(1f, progress));
		return new Color(
				Math.round(start.getRed() + (end.getRed() - start.getRed()) * clamped),
				Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * clamped),
				Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * clamped));
	}

	private static void installComboHoverFeedback(JComboBox<?> comboBox, Color normal, Color hover) {
		if (hover == null) {
			return;
		}
		Object previous = comboBox.getClientProperty("WrapperInstaller.comboHoverListener");
		if (previous instanceof java.awt.event.MouseListener listener) {
			comboBox.removeMouseListener(listener);
		}
		java.awt.event.MouseListener listener = new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(java.awt.event.MouseEvent event) {
				comboBox.putClientProperty("WrapperInstaller.comboHovered", true);
				animateComboHoverColor(comboBox, normal, hover);
				comboBox.repaint();
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent event) {
				comboBox.putClientProperty("WrapperInstaller.comboHovered", false);
				animateComboHoverColor(comboBox, hover, normal);
				comboBox.repaint();
			}
		};
		comboBox.putClientProperty("WrapperInstaller.comboHoverListener", listener);
		comboBox.addMouseListener(listener);
	}

	private static void installHoverFeedback(JComponent child, JComboBox<?> comboBox, Color hover) {
		if (hover == null) {
			return;
		}
		Object previous = child.getClientProperty("WrapperInstaller.comboHoverListener");
		if (previous instanceof java.awt.event.MouseListener listener) {
			child.removeMouseListener(listener);
		}
		java.awt.event.MouseListener listener = new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(java.awt.event.MouseEvent event) {
				comboBox.putClientProperty("WrapperInstaller.comboHovered", true);
				animateComboHoverColor(comboBox, comboBox.getBackground(), hover);
				comboBox.repaint();
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent event) {
				comboBox.putClientProperty("WrapperInstaller.comboHovered", false);
				animateComboHoverColor(comboBox, hover, comboBox.getBackground());
				comboBox.repaint();
			}
		};
		child.putClientProperty("WrapperInstaller.comboHoverListener", listener);
		child.addMouseListener(listener);
	}

	private static void animateComboHoverColor(JComboBox<?> comboBox, Color start, Color target) {
		Object previous = comboBox.getClientProperty("WrapperInstaller.comboHoverTimer");
		if (previous instanceof Timer timer) {
			timer.stop();
		}
		Color initial = (Color) comboBox.getClientProperty("WrapperInstaller.comboHoverColor");
		if (initial == null) {
			initial = start;
		}
		Color initialColor = initial;
		long started = System.currentTimeMillis();
		Timer timer = new Timer(HOVER_ANIMATION_INTERVAL, event -> {
			float progress = Math.min(1f,
					(System.currentTimeMillis() - started) / (float) HOVER_ANIMATION_DURATION);
			comboBox.putClientProperty("WrapperInstaller.comboHoverColor",
					interpolateColor(initialColor, target, progress));
			comboBox.repaint();
			if (progress >= 1f) {
				((Timer) event.getSource()).stop();
				comboBox.putClientProperty("WrapperInstaller.comboHoverTimer", null);
			}
		});
		comboBox.putClientProperty("WrapperInstaller.comboHoverTimer", timer);
		timer.start();
	}

	static javax.swing.border.Border flatControlBorder(Theme theme) {
		Color borderColor = theme == Theme.LIGHT ? new Color(170, 170, 170) : new Color(105, 105, 105);
		return BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(borderColor),
				BorderFactory.createEmptyBorder(4, 8, 4, 8));
	}

	static javax.swing.border.Border buttonBorder(Theme theme) {
		Color highlight = theme == Theme.LIGHT ? Color.WHITE : new Color(125, 125, 125);
		Color shadow = theme == Theme.LIGHT ? new Color(145, 145, 145) : new Color(30, 30, 30);
		return BorderFactory.createCompoundBorder(
				new RoundedBevelBorder(highlight, shadow),
				BorderFactory.createEmptyBorder(3, 8, 3, 8));
	}

	private static final class RoundedBevelBorder extends javax.swing.border.AbstractBorder {
		private final Color highlight;
		private final Color shadow;

		private RoundedBevelBorder(Color highlight, Color shadow) {
			this.highlight = highlight;
			this.shadow = shadow;
		}

		@Override
		public Insets getBorderInsets(Component component) {
			return new Insets(2, 2, 2, 2);
		}

		@Override
		public Insets getBorderInsets(Component component, Insets insets) {
			insets.set(2, 2, 2, 2);
			return insets;
		}

		@Override
		public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
			Graphics2D graphics2D = (Graphics2D) graphics.create();
			try {
				graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				graphics2D.setColor(highlight);
				graphics2D.drawRoundRect(x, y, width - 2, height - 2, 9, 9);
				graphics2D.setColor(shadow);
				graphics2D.drawRoundRect(x + 1, y + 1, width - 2, height - 2, 9, 9);
			} finally {
				graphics2D.dispose();
			}
		}
	}

	private static final class RoundedButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
		@Override
		public void paint(Graphics graphics, JComponent component) {
			Graphics2D graphics2D = (Graphics2D) graphics.create();
			try {
				graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				graphics2D.setColor(component.getBackground());
				graphics2D.fillRoundRect(1, 1, component.getWidth() - 2, component.getHeight() - 2, 9, 9);
			} finally {
				graphics2D.dispose();
			}
			super.paint(graphics, component);
		}
	}

	private static final class FocusAwareComboBoxBorder extends LineBorder {
		private final JComboBox<?> comboBox;

		private FocusAwareComboBoxBorder(Theme theme, JComboBox<?> comboBox) {
			super(theme == Theme.LIGHT ? new Color(170, 170, 170) : new Color(105, 105, 105));
			this.comboBox = comboBox;
		}

		@Override
		public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
			Color selectionBackground = UIManager.getColor("ComboBox.selectionBackground");
			if (comboBox.hasFocus() && selectionBackground != null) {
				graphics.setColor(selectionBackground);
				graphics.drawRect(x, y, width - 1, height - 1);
			} else {
				super.paintBorder(component, graphics, x, y, width, height);
			}
		}
	}

	private static final class FlatComboBoxUI extends BasicComboBoxUI {
		@Override
		protected ListCellRenderer<Object> createRenderer() {
			return new DefaultListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index,
						boolean selected, boolean cellHasFocus) {
					Component renderer = super.getListCellRendererComponent(list, value, index, selected, cellHasFocus);
					setOpaque(index >= 0);
					setBorder(BorderFactory.createEmptyBorder());
					return renderer;
				}
			};
		}

		@Override
		protected JButton createArrowButton() {
			JButton button = new JButton();
			button.setBorder(BorderFactory.createEmptyBorder());
			button.setContentAreaFilled(false);
			return button;
		}

		@Override
		public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
			Color selectionBackground = UIManager.getColor("ComboBox.selectionBackground");
			boolean hovered = Boolean.TRUE.equals(comboBox.getClientProperty("WrapperInstaller.comboHovered"));
			Color hoverColor = (Color) comboBox.getClientProperty("WrapperInstaller.comboHoverColor");
			Color background = hasFocus && selectionBackground != null ? selectionBackground
					: hovered && hoverColor != null ? hoverColor : comboBox.getBackground();
			graphics.setColor(background);
			int width = Math.max(bounds.width, comboBox.getWidth());
			int height = Math.max(bounds.height, comboBox.getHeight());
			graphics.fillRect(0, 0, width, height);
		}
	}

	private static void applyThemeDefaults(Theme theme) {
		try {
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		} catch (Exception exception) {
			LOGGER.log(Level.WARNING, "Failed to set the cross-platform look and feel", exception);
		}
		if (theme == Theme.LIGHT) {
			setLightDefaults();
		} else {
			setDarkDefaults();
		}
	}


	private static void setLightDefaults() {
		setThemeDefaults(Color.WHITE, Color.BLACK, new Color(238, 238, 238),
				new Color(184, 207, 229), Color.BLACK);
	}

	private static void setDarkDefaults() {
		setThemeDefaults(new Color(43, 43, 43), new Color(235, 235, 235), new Color(65, 65, 65),
				new Color(90, 105, 120), Color.WHITE);
	}

	private static void setThemeDefaults(Color background, Color foreground, Color control,
			Color selectionBackground, Color selectionForeground) {
		UIManager.put("Panel.background", background);
		UIManager.put("Panel.foreground", foreground);
		UIManager.put("Label.foreground", foreground);
		UIManager.put("MenuBar.background", background);
		UIManager.put("MenuBar.foreground", foreground);
		UIManager.put("Menu.background", background);
		UIManager.put("Menu.foreground", foreground);
		UIManager.put("MenuItem.background", background);
		UIManager.put("MenuItem.foreground", foreground);
		UIManager.put("MenuItem.selectionBackground", selectionBackground);
		UIManager.put("MenuItem.selectionForeground", selectionForeground);
		UIManager.put("RadioButtonMenuItem.background", background);
		UIManager.put("RadioButtonMenuItem.foreground", foreground);
		UIManager.put("RadioButtonMenuItem.selectionBackground", selectionBackground);
		UIManager.put("RadioButtonMenuItem.selectionForeground", selectionForeground);
		UIManager.put("TextField.background", control);
		UIManager.put("TextField.foreground", foreground);
		UIManager.put("TextArea.background", control);
		UIManager.put("TextArea.foreground", foreground);
		UIManager.put("TextArea.caretForeground", foreground);
		UIManager.put("TextArea.selectionBackground", selectionBackground);
		UIManager.put("TextArea.selectionForeground", selectionForeground);
		UIManager.put("ComboBox.background", control);
		UIManager.put("ComboBox.foreground", foreground);
		UIManager.put("ComboBox.focus", control);
		UIManager.put("ComboBox.buttonBackground", control);
		UIManager.put("ComboBox.buttonHighlight", control);
		UIManager.put("ComboBox.buttonDarkShadow", control.darker());
		UIManager.put("Button.background", control);
		UIManager.put("Button.foreground", foreground);
		UIManager.put("Button.select", selectionBackground);
		UIManager.put("Button.focus", selectionBackground);
		UIManager.put("ScrollPane.background", background);
		UIManager.put("ScrollPane.foreground", foreground);
		UIManager.put("Viewport.background", background);
		UIManager.put("Separator.background", control);
		UIManager.put("Separator.foreground", control);
		UIManager.put("TitledBorder.titleColor", foreground);
		UIManager.put("OptionPane.background", background);
		UIManager.put("OptionPane.foreground", foreground);
		UIManager.put("OptionPane.messageForeground", foreground);
		UIManager.put("OptionPane.buttonBackground", control);
		UIManager.put("OptionPane.buttonForeground", foreground);
		UIManager.put("ComboBox.selectionBackground", selectionBackground);
		UIManager.put("ComboBox.selectionForeground", selectionForeground);
		UIManager.put("List.background", control);
		UIManager.put("List.foreground", foreground);
		UIManager.put("Table.background", control);
		UIManager.put("Table.foreground", foreground);
		UIManager.put("ToolTip.background", control);
		UIManager.put("ToolTip.foreground", foreground);
	}

	private JPanel createInstallerPanel(JFrame frame) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		makeBackgroundFocusable(panel);
		addComponentsToPane(panel);
		JComboBox<String> versions = new JComboBox<>(new String[] {"Loading NeoForge versions..."});
		JComboBox<String> action = new JComboBox<>(new String[] {"Client", "Server", "Fat installer"});
		action.setPrototypeDisplayValue("Fat installer");
		JTextField directory = new JTextField(new File(System.getProperty("user.dir")).getAbsolutePath());
		directory.setEditable(false);
		JComboBox<String> targetMode = new JComboBox<>(new String[] {"Custom directory", "Launcher profile"});
		targetMode.setPrototypeDisplayValue("Custom directory");
		JComboBox<String> profileType = new JComboBox<>(new String[] {"CurseForge", "Modrinth"});
		profileType.setPrototypeDisplayValue("CurseForge");
		JComboBox<Path> profiles = new JComboBox<>();
		profiles.setPrototypeDisplayValue(Path.of("Example profile"));
		profiles.setEnabled(false);
		JComboBox<String> profilesLocationMode = new JComboBox<>(new String[] {
				"Detect launcher profile locations", "Use custom profile location"});
		profilesLocationMode.setPrototypeDisplayValue("Use custom profile location");
		JTextField profilesLocation = new JTextField();
		profilesLocation.setEditable(false);
		JButton chooseProfilesLocation = new JButton("Browse…");
		JTextArea status = new JTextArea("Loading versions from the official NeoForge Maven repository...", 8, 40);
		status.setEditable(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		JLabel completion = new JLabel("Ready");
		completion.setForeground(Color.GRAY);
		JButton browse = new JButton("Choose directory");
		JButton install = new JButton("Download and install");
  JButton installJava = new JButton("Install Microsoft OpenJDK " + JavaRuntimeManager.recommendedMajor() + " with Jabba");
  installJava.setToolTipText("Install Microsoft OpenJDK 25 through Jabba and add it to your user PATH.");

		JPanel installationPanel = new JPanel(new GridBagLayout());
		installationPanel.setBorder(BorderFactory.createTitledBorder("Installation"));
		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		toolbar.add(new JLabel("Install action:"));
		toolbar.add(Box.createHorizontalStrut(6));
		toolbar.add(action);
		toolbar.add(Box.createHorizontalStrut(6));
		toolbar.add(install);
		installationPanel.add(toolbar, constraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL));
		installationPanel.add(new JLabel("NeoForge version:"), constraints(0, 1, 0, 0, GridBagConstraints.NONE));
		installationPanel.add(versions, constraints(0, 2, 1, 0, GridBagConstraints.HORIZONTAL));
		panel.add(installationPanel, constraints(0, 2, 1, 0, GridBagConstraints.HORIZONTAL));

        JPanel javaPanel = new JPanel(new BorderLayout(10, 0));
        javaPanel.setBorder(BorderFactory.createTitledBorder("Java runtime"));
        javaPanel.add(new JLabel("Install the required Microsoft OpenJDK:"),
                BorderLayout.CENTER);
        javaPanel.add(installJava, BorderLayout.EAST);
        panel.add(javaPanel, constraints(0, 3, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel launcherAppsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		launcherAppsPanel.setBorder(BorderFactory.createTitledBorder("Launcher apps"));
		launcherAppsPanel.add(new JLabel("Install a launcher app:"));
		JButton installCurseForge = new JButton("Install CurseForge app");
		JButton installModrinth = new JButton("Install Modrinth app");
		launcherAppsPanel.add(installCurseForge);
		launcherAppsPanel.add(installModrinth);
		panel.add(launcherAppsPanel, constraints(0, 4, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel destinationPanel = new JPanel(new GridBagLayout());
		destinationPanel.setBorder(BorderFactory.createTitledBorder("Destination"));
		destinationPanel.add(new JLabel("Install destination:"), constraints(0, 0, 0, 0, GridBagConstraints.NONE));
		JPanel targetModePanel = new JPanel(new BorderLayout(6, 0));
		targetModePanel.add(targetMode, BorderLayout.WEST);
		destinationPanel.add(targetModePanel, constraints(0, 1, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel profilePanel = new JPanel(new BorderLayout(6, 0));
		profilePanel.add(profileType, BorderLayout.WEST);
		profilePanel.add(profiles, BorderLayout.CENTER);
		destinationPanel.add(profilePanel, constraints(0, 2, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel profilesLocationPanel = new JPanel(new BorderLayout(6, 6));
		JPanel profilesLocationDetails = new JPanel(new BorderLayout(6, 0));
		profilesLocationDetails.add(new JLabel("Profile location:"), BorderLayout.WEST);
		profilesLocationDetails.add(profilesLocation, BorderLayout.CENTER);
		profilesLocationPanel.add(profilesLocationMode, BorderLayout.NORTH);
		profilesLocationPanel.add(profilesLocationDetails, BorderLayout.CENTER);
		profilesLocationPanel.add(chooseProfilesLocation, BorderLayout.EAST);
		destinationPanel.add(profilesLocationPanel, constraints(0, 3, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel directoryPanel = new JPanel(new BorderLayout(6, 0));
		directoryPanel.add(directory, BorderLayout.CENTER);
		directoryPanel.add(browse, BorderLayout.EAST);
		destinationPanel.add(directoryPanel, constraints(0, 4, 1, 0, GridBagConstraints.HORIZONTAL));
		panel.add(destinationPanel, constraints(0, 5, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel activityPanel = new JPanel(new BorderLayout(0, 6));
		activityPanel.setBorder(BorderFactory.createTitledBorder("Activity"));
		activityPanel.add(new JScrollPane(status), BorderLayout.CENTER);

		StatusBox statusBox = new StatusBox();
		activityPanel.add(statusBox, BorderLayout.SOUTH);
		activityPanel.add(completion, BorderLayout.NORTH);
		panel.add(activityPanel, constraints(0, 6, 1, 1, GridBagConstraints.BOTH));
		profilePanel.setVisible(false);
		profilesLocationPanel.setVisible(false);

		final File[] target = {new File(directory.getText())};
		final File[] customTarget = {target[0]};
		Runnable refreshProfiles = () -> {
			LauncherProfileLocator.Type type = profileType.getSelectedIndex() == 0
					? LauncherProfileLocator.Type.CURSEFORGE : LauncherProfileLocator.Type.MODRINTH;
			profiles.removeAllItems();
			boolean customLocation = profilesLocationMode.getSelectedIndex() == 1;
			List<Path> roots = customLocation
					? customProfileRoots(profilesLocation.getText())
					: LauncherProfileLocator.existingProfileRoots(type, Path.of(System.getProperty("user.home")),
							System.getProperty("os.name", ""));
			List<Path> found = roots.stream()
					.flatMap(root -> LauncherProfileLocator.findProfiles(root).stream())
					.toList();
			if (!customLocation) {
				profilesLocation.setText(roots.isEmpty() ? "No detected location" : roots.get(0).toString());
			}
			found.forEach(profiles::addItem);
			boolean launcherProfileSelected = targetMode.getSelectedIndex() == 1;
			profiles.setEnabled(launcherProfileSelected);
			chooseProfilesLocation.setEnabled(launcherProfileSelected && customLocation);
			if (launcherProfileSelected && profiles.getItemCount() > 0) {
				profiles.setSelectedIndex(0);
				target[0] = profiles.getItemAt(0).toFile();
				directory.setText(target[0].getAbsolutePath());
			}
		};
		Runnable updateProfilesLocationControls = () -> {
			boolean customLocation = profilesLocationMode.getSelectedIndex() == 1;
			profilesLocation.setText(customLocation ? "" : "Detecting...");
			profilesLocation.setToolTipText(customLocation
					? "Choose the folder that contains the CurseForge or Modrinth profile folders."
					: "Detected profile locations for the selected launcher.");
			chooseProfilesLocation.setEnabled(customLocation && targetMode.getSelectedIndex() == 1);
			refreshProfiles.run();
		};
		Runnable updateDestinationControls = () -> {
			boolean launcherProfileSelected = targetMode.getSelectedIndex() == 1;
			profilePanel.setVisible(launcherProfileSelected);
			profilesLocationPanel.setVisible(launcherProfileSelected);
			directoryPanel.setVisible(!launcherProfileSelected);
			profiles.setEnabled(launcherProfileSelected);
   if (launcherProfileSelected) {
				refreshProfiles.run();
			} else {
				target[0] = customTarget[0];
				directory.setText(target[0].getAbsolutePath());
			}
			panel.revalidate();
			panel.repaint();
		};
		targetMode.addActionListener(event -> updateDestinationControls.run());
  profilesLocationMode.addActionListener(event -> updateProfilesLocationControls.run());
		profileType.addActionListener(event -> refreshProfiles.run());
		profiles.addActionListener(event -> {
			Path selected = (Path) profiles.getSelectedItem();
			if (selected != null && targetMode.getSelectedIndex() == 1) {
				target[0] = selected.toFile();
				directory.setText(target[0].getAbsolutePath());
			}
		});
		chooseProfilesLocation.addActionListener(event -> {
			File start = profilesLocation.getText().isBlank() || profilesLocation.getText().equals("Detecting...")
					? target[0] : new File(profilesLocation.getText());
			JFileChooser chooser = new JFileChooser(start);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
				profilesLocation.setText(chooser.getSelectedFile().getAbsolutePath());
				refreshProfiles.run();
			}
		});
		installCurseForge.addActionListener(event ->
				installLauncherApp(frame, LauncherApp.CURSEFORGE, status, completion));
		installModrinth.addActionListener(event ->
				installLauncherApp(frame, LauncherApp.MODRINTH, status, completion));
		browse.addActionListener(event -> {
			JFileChooser chooser = new JFileChooser(target[0]);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
				target[0] = chooser.getSelectedFile();
				if (targetMode.getSelectedIndex() == 0) {
					customTarget[0] = target[0];
				}
				directory.setText(target[0].getAbsolutePath());
				targetMode.setSelectedIndex(0);
			}
		});
  installJava.addActionListener(event -> startJavaInstallation(frame, installJava, status, completion, statusBox));
  install.addActionListener(event -> {
            if (targetMode.getSelectedIndex() == 1 && profiles.getSelectedItem() == null) {
				completion.setText("Profile required");
				completion.setForeground(Color.RED);
				status.append("Select an existing CurseForge or Modrinth profile first.\n");
				return;
			}
         			startInstallation(frame, versions, action, target[0], targetMode.getSelectedIndex() == 1,
     status, completion, install, statusBox);
        });
		loadVersions(versions, status);
		return panel;
	}

	static void makeBackgroundFocusable(JPanel panel) {
		panel.setFocusable(true);
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent event) {
				panel.requestFocusInWindow();
			}
		});
	}

	private static List<Path> customProfileRoots(String location) {
		if (location == null || location.isBlank() || location.equals("Detecting...")) {
			return List.of();
		}
		try {
			Path root = Path.of(location.trim());
			return java.nio.file.Files.isDirectory(root) ? List.of(root) : List.of();
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	private static void loadVersions(JComboBox<String> versions, JTextArea status) {
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				return NeoForgeVersionApi.getVersions();
			}

			@Override
			protected void done() {
				try {
					versions.removeAllItems();
					for (String version : get()) {
						versions.addItem(version);
					}
					status.setText("Select an action and destination, then download the official installer.\n");
				} catch (Exception exception) {
					versions.removeAllItems();
					status.setText("Could not load NeoForge versions: " + exception.getMessage());
				}
			}
		}.execute();
	}

 private static void startJavaInstallation(JFrame frame, JButton installJava, JTextArea status, JLabel completion,
            StatusBox statusBox) {
        installJava.setEnabled(false);
        completion.setText("Installing Java...");
        completion.setForeground(new Color(180, 110, 0));
        statusBox.setState(StatusBox.State.IN_PROGRESS);
        status.setText("Installing Microsoft OpenJDK " + JavaRuntimeManager.recommendedMajor() + " with Jabba...\n");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                JavaRuntimeManager.ensureJdk(JavaRuntimeManager.recommendedMajor(), line -> publish(line + "\n"));
                return null;
            }

            @Override
            protected void process(List<String> messages) {
                messages.forEach(status::append);
            }

            @Override
            protected void done() {
                installJava.setEnabled(true);
                frame.revalidate();
                frame.repaint();
                try {
                    get();
                    status.append("Java installation completed successfully.\n");
                    completion.setText("Java installation complete");
                    completion.setForeground(new Color(0, 128, 0));
                    statusBox.setState(StatusBox.State.SUCCESS);
                } catch (Exception exception) {
                    Throwable failure = exception instanceof ExecutionException && exception.getCause() != null
                            ? exception.getCause() : exception;
                    String message = failure.getMessage() == null || failure.getMessage().isBlank()
                            ? failure.toString() : failure.getMessage();
                    status.append("Java installation failed: " + message + "\n");
                    completion.setText("Java installation failed");
                    completion.setForeground(Color.RED);
                    statusBox.setState(StatusBox.State.FAILURE);
                    JOptionPane.showMessageDialog(frame, message, "Java installation failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

 private static void startInstallation(JFrame frame, JComboBox<String> versions, JComboBox<String> action,
            File target, boolean launcherProfile, JTextArea status, JLabel completion,
            JButton install, StatusBox statusBox) {
        String version = (String) versions.getSelectedItem();
        if (!launcherProfile && (version == null || version.startsWith("Loading"))) {
            return;
        }
		String selectedAction = switch ((String) Objects.requireNonNull(action.getSelectedItem())) {
			case "Server" -> "--installServer";
			case "Fat installer" -> "--fat-installer";
			default -> "--installClient";
		};
		install.setEnabled(false);
		completion.setText("Installing...");
		completion.setForeground(new Color(180, 110, 0));
		statusBox.setState(StatusBox.State.IN_PROGRESS);
  status.setText(launcherProfile
          ? "Reading the selected profile and preparing AutoModpack...\n"
          : "Downloading official NeoForge " + version + " installer...\n");
		 new SwingWorker<Integer, String>() {
   @Override
   protected Integer doInBackground() throws Exception {
       if (!shouldInstallNeoForge(launcherProfile)) {
           LauncherProfileLocator.ProfileInfo profile = LauncherProfileLocator.readProfile(target.toPath());
           if (profile == null) {
               throw new IllegalArgumentException("Selected profile has no supported game version and loader metadata");
           }
           publish("Existing " + profile.loader() + " profile detected for Minecraft "
                   + profile.gameVersion() + ". Skipping NeoForge installation.\n");
           Installation.installAutomodpackOrThrow(profile.gameVersion(), profile.loader(), target.toPath(),
                   line -> publish(line + "\n"));
           publish("AutoModpack installation completed successfully.\n");
           return 0;
       }
				if ("--installClient".equals(selectedAction)) {
					String preflightError = clientInstallPreflightError(target);
					if (preflightError != null) {
						throw new IllegalArgumentException(preflightError);
					}
				}
       File installerJar = OfficialInstallerLauncher.download(version);
				publish("Downloaded official installer: " + installerJar + "\nStarting installation...\n");
				File destination = selectedAction.equals("--fat-installer")
						? new File(target, "neoforge-" + version + "-fat.jar") : target;
    return runInstallation(installerJar, selectedAction, version, destination, null,
						line -> publish(line + "\n"));
			}

			@Override
			protected void process(List<String> messages) {
				messages.forEach(status::append);
			}

			@Override
			protected void done() {
				install.setEnabled(true);
				try {
					int result = get();
					if (result == 0) {
						status.append("Installation completed successfully.\n");
						completion.setText("Installation complete");
						completion.setForeground(new Color(0, 128, 0));
						statusBox.setState(StatusBox.State.SUCCESS);
					} else {
						status.append("The official installer did not complete (exit code " + result
								+ "). No successful installation was reported.\n");
						completion.setText("Installation failed (exit code " + result + ")");
						completion.setForeground(Color.RED);
						statusBox.setState(StatusBox.State.FAILURE);
					}
				} catch (Exception exception) {
					Throwable failure = exception instanceof ExecutionException && exception.getCause() != null
							? exception.getCause() : exception;
					String message = failure.getMessage() == null || failure.getMessage().isBlank()
							? failure.toString() : failure.getMessage();
					status.append("Installation failed: " + message + "\n");
					completion.setText("Installation failed");
					completion.setForeground(Color.RED);
					statusBox.setState(StatusBox.State.FAILURE);
					JOptionPane.showMessageDialog(frame, message, "Installation failed", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	static final class StatusBox extends JPanel {
		enum State { IDLE, SUCCESS, FAILURE, IN_PROGRESS }

		private final JLabel label = new JLabel("Ready");
		private final JPanel indicator = new JPanel();
		private State state = State.IDLE;

		StatusBox() {
			setLayout(new GridBagLayout());
			setBorder(BorderFactory.createTitledBorder("Status"));
			indicator.setPreferredSize(new Dimension(16, 16));
			label.setFont(label.getFont().deriveFont(Font.PLAIN));
			add(indicator, constraints(0, 0, 0, 0, GridBagConstraints.NONE));
			add(label, constraints(1, 0, 1, 0, GridBagConstraints.HORIZONTAL));
			setState(State.IDLE);
		}

		@Override
		public void updateUI() {
			super.updateUI();
			setBorder(BorderFactory.createTitledBorder("Status"));
			if (indicator != null) {
				setState(state);
			}
		}

		void applyTheme(Theme theme) {
			Color background = theme == Theme.LIGHT ? Color.WHITE : new Color(43, 43, 43);
			Color foreground = theme == Theme.LIGHT ? Color.BLACK : new Color(235, 235, 235);
			setBackground(background);
			setForeground(foreground);
			label.setForeground(foreground);
			setBorder(BorderFactory.createTitledBorder("Status"));
		}

		void setState(State state) {
			this.state = state;
			Color color = switch (state) {
				case IDLE -> Color.GRAY;
				case SUCCESS -> Color.GREEN;
				case FAILURE -> Color.RED;
				case IN_PROGRESS -> Color.ORANGE;
			};
			label.setText(switch (state) {
				case IDLE -> "Idle";
				case SUCCESS -> "Success";
				case FAILURE -> "Failure";
				case IN_PROGRESS -> "In progress";
			});
			indicator.setBackground(color);
		}
	}

}