package net.minecraftforge.installer;

import java.awt.*;
import javax.swing.*;
import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import net.minecraftforge.installer.automodpack.Installation;

public final class WrapperInstaller {
    private static final Logger LOGGER = Logger.getLogger(WrapperInstaller.class.getName());

    enum Theme {
        LIGHT, DARK
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
            SwingUtilities.invokeLater(() -> new WrapperInstaller().createAndShowGUI("Ready", Color.GRAY));
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
        if ("--installClient".equals(action) || "--installServer".equals(action) || "--fat-installer".equals(action)) {
            return action;
        }
        throw new IllegalArgumentException("Unsupported installer action");
    }

    private static String validateVersion(String version) {
        NeoForgeVersionApi.installerUrl(version);
        return version;
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
        int exitCode = OfficialInstallerLauncher.run(installer, action, target, output);
        if (exitCode == 0 && installsGame(action)) {
            output.accept("NeoForge installation finished. Checking for AutoModpack... ");
            Installation.installAutomodpack(minecraftVersion(neoForgeVersion), target.toPath(), output);
        }
        return exitCode;
    }

    static boolean installsGame(String action) {
        return "--installClient".equals(action) || "--installServer".equals(action);
    }

    static boolean shouldInstallNeoForge(boolean launcherProfile) {
        return !launcherProfile;
    }

    static String minecraftVersion(String neoForgeVersion) {
        int separator = neoForgeVersion.indexOf('-');
        String minecraftVersion = separator >= 0 ? neoForgeVersion.substring(0, separator) : neoForgeVersion;
        String[] parts = minecraftVersion.split("\\.");
        if (separator >= 0 || parts.length <= 2) {
            return minecraftVersion;
        }
        return parts[0] + "." + parts[1];
    }

	public static void addComponentsToPane(Container pane) {
		pane.setLayout(new GridBagLayout());
		pane.add(new JLabel("Use the controls below to select a version and destination."),
				constraints(0, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
	}

	private static GridBagConstraints constraints(int x, int y, int width, int height,
			double weightX, double weightY, int fill) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = x;
		constraints.gridy = y;
		constraints.gridwidth = width;
		constraints.gridheight = height;
		constraints.weightx = weightX;
		constraints.weighty = weightY;
		constraints.fill = fill;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.insets = new Insets(6, 8, 6, 8);
		return constraints;
	}

	private JPanel createStatusIndicator(Color color) {
		return new JPanel() {
			@Serial private static final long serialVersionUID = 1L;

			@Override
			public Dimension getPreferredSize() {
				return new Dimension(16, 16);
			}

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.setColor(color);
				g.fillOval(0, 0, getWidth(), getHeight());
			}
		};
	}

	private void createAndShowGUI(String label, Color color) {
		applyThemeDefaults(Theme.LIGHT);
		JFrame frame = new JFrame("Installer");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(createInstallerPanel(frame, label, color));
		frame.setJMenuBar(createMenuBar(frame));
		applyComponentTheme(frame, Theme.LIGHT);
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

	private void applyTheme(JFrame frame, Theme theme) {
		try {
			applyThemeDefaults(theme);
			SwingUtilities.updateComponentTreeUI(frame);
			applyComponentTheme(frame, theme);
			frame.pack();
			frame.repaint();
		} catch (Exception exception) {
			LOGGER.log(Level.WARNING, "Failed to apply " + themeName(theme) + " theme", exception);
		}
	}

	private static void applyComponentTheme(Component component, Theme theme) {
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
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				applyComponentTheme(child, theme);
			}
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

	private JPanel createInstallerPanel(JFrame frame, String initialLabel, Color initialColor) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		addComponentsToPane(panel);
		JComboBox<String> versions = new JComboBox<>(new String[] {"Loading NeoForge versions..."});
		JComboBox<String> action = new JComboBox<>(new String[] {"Client", "Server", "Fat installer"});
		JTextField directory = new JTextField(new File(System.getProperty("user.dir")).getAbsolutePath());
		directory.setEditable(false);
		JComboBox<String> targetMode = new JComboBox<>(new String[] {"Custom directory", "Launcher profile"});
		JComboBox<String> profileType = new JComboBox<>(new String[] {"CurseForge", "Modrinth"});
		JComboBox<Path> profiles = new JComboBox<>();
		profiles.setEnabled(false);
		JComboBox<String> profilesLocationMode = new JComboBox<>(new String[] {
				"Detect launcher profile locations", "Use custom profile location"});
		JTextField profilesLocation = new JTextField();
		profilesLocation.setEditable(false);
		JButton chooseProfilesLocation = new JButton("Browse…");
		JTextArea status = new JTextArea("Loading versions from the official NeoForge Maven repository...", 8, 40);
		status.setEditable(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		JLabel completion = new JLabel(initialLabel);
		completion.setForeground(initialColor);
		JButton browse = new JButton("Choose directory");
		JButton install = new JButton("Download and install");

		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		toolbar.add(new JLabel("Install action:"));
		toolbar.add(Box.createHorizontalStrut(6));
		toolbar.add(action);
		toolbar.add(Box.createHorizontalStrut(6));
		toolbar.add(install);
		panel.add(toolbar, constraints(0, 2, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
		panel.add(new JLabel("NeoForge version:"), constraints(0, 3, 1, 1, 0, 0, GridBagConstraints.NONE));
		panel.add(versions, constraints(0, 4, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
		panel.add(new JLabel("Install destination:"), constraints(0, 5, 1, 1, 0, 0, GridBagConstraints.NONE));

		JPanel targetModePanel = new JPanel(new BorderLayout(6, 0));
		targetModePanel.add(targetMode, BorderLayout.WEST);
		panel.add(targetModePanel, constraints(0, 6, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel profilePanel = new JPanel(new BorderLayout(6, 0));
		profilePanel.add(profileType, BorderLayout.WEST);
		profilePanel.add(profiles, BorderLayout.CENTER);
		panel.add(profilePanel, constraints(0, 7, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel profilesLocationPanel = new JPanel(new BorderLayout(6, 6));
		JPanel profilesLocationDetails = new JPanel(new BorderLayout(6, 0));
		profilesLocationDetails.add(new JLabel("Profile location:"), BorderLayout.WEST);
		profilesLocationDetails.add(profilesLocation, BorderLayout.CENTER);
		profilesLocationPanel.add(profilesLocationMode, BorderLayout.NORTH);
		profilesLocationPanel.add(profilesLocationDetails, BorderLayout.CENTER);
		profilesLocationPanel.add(chooseProfilesLocation, BorderLayout.EAST);
		panel.add(profilesLocationPanel, constraints(0, 8, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));

		JPanel directoryPanel = new JPanel(new BorderLayout(6, 0));
		directoryPanel.add(directory, BorderLayout.CENTER);
		directoryPanel.add(browse, BorderLayout.EAST);
		panel.add(directoryPanel, constraints(0, 9, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));

		panel.add(new JScrollPane(status), constraints(0, 10, 1, 1, 1, 1, GridBagConstraints.BOTH));

		StatusBox statusBox = new StatusBox();
		panel.add(statusBox, constraints(0, 11, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
		panel.add(completion, constraints(0, 12, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
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

 private static void startInstallation(JFrame frame, JComboBox<String> versions, JComboBox<String> action,
            File target, boolean launcherProfile, JTextArea status, JLabel completion, JButton install,
            StatusBox statusBox) {
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
           Installation.installAutomodpack(profile.gameVersion(), profile.loader(), target.toPath(),
                   line -> publish(line + "\n"));
           return 0;
       }
       File installerJar = OfficialInstallerLauncher.download(version);
				publish("Downloaded official installer: " + installerJar + "\nStarting installation...\n");
				File destination = selectedAction.equals("--fat-installer")
						? new File(target, "neoforge-" + version + "-fat.jar") : target;
				return runInstallation(installerJar, selectedAction, version, destination, line -> publish(line + "\n"));
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
						status.append("Official installer exited with code " + result + ".\n");
						completion.setText("Installation failed (exit code " + result + ")");
						completion.setForeground(Color.RED);
						statusBox.setState(StatusBox.State.FAILURE);
					}
				} catch (Exception exception) {
					status.append("Installation failed: " + exception.getMessage() + "\n");
					completion.setText("Installation failed");
					completion.setForeground(Color.RED);
					statusBox.setState(StatusBox.State.FAILURE);
					JOptionPane.showMessageDialog(frame, exception.toString(), "Installation failed", JOptionPane.ERROR_MESSAGE);
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
			add(indicator, constraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NONE));
			add(label, constraints(1, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
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