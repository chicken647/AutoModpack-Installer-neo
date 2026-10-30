package net.minecraftforge.installer;

import java.awt.*;
import javax.swing.*;
import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.installer.automodpack.Installation;

public final class WrapperInstaller {
    private static final Logger LOGGER = Logger.getLogger(WrapperInstaller.class.getName());

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
		JFrame frame = new JFrame("NeoForge Installer");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(createInstallerPanel(frame, label, color));
		frame.pack();
		frame.setMinimumSize(new Dimension(640, 460));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
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

		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		toolbar.add(new JLabel("Install action:"));
		toolbar.add(action);
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
		JPanel profilesLocationPanel = new JPanel(new BorderLayout(6, 0));
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
			startInstallation(frame, versions, action, target[0], status, completion, install, statusBox);
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
			File target, JTextArea status, JLabel completion, JButton install, StatusBox statusBox) {
		String version = (String) versions.getSelectedItem();
		if (version == null || version.startsWith("Loading")) {
			return;
		}
		String selectedAction = switch ((String) action.getSelectedItem()) {
			case "Server" -> "--installServer";
			case "Fat installer" -> "--fat-installer";
			default -> "--installClient";
		};
		install.setEnabled(false);
		completion.setText("Installing...");
		completion.setForeground(new Color(180, 110, 0));
		statusBox.setState(StatusBox.State.IN_PROGRESS);
		status.setText("Downloading official NeoForge " + version + " installer...\n");
		new SwingWorker<Integer, String>() {
			@Override
			protected Integer doInBackground() throws Exception {
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

	private static final class StatusBox extends JPanel {
		enum State { IDLE, SUCCESS, FAILURE, IN_PROGRESS }

		private final JLabel label = new JLabel("Ready");
		private final JPanel indicator = new JPanel();

		StatusBox() {
			setLayout(new GridBagLayout());
			setBorder(BorderFactory.createTitledBorder("Status"));
			indicator.setPreferredSize(new Dimension(16, 16));
			add(indicator, constraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NONE));
			add(label, constraints(1, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL));
			setState(State.IDLE);
		}

		void setState(State state) {
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

    private static void legacyShowWindow() {
        JFrame frame = new JFrame("NeoForge Installer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JComboBox<String> versions = new JComboBox<>();
        versions.addItem("Loading NeoForge versions...");

        JRadioButton client = new JRadioButton("Client", true);
        JRadioButton server = new JRadioButton("Server");
        JRadioButton fat = new JRadioButton("Fat installer");
		JRadioButton custom = new JRadioButton("Custom installer");
        ButtonGroup modes = new ButtonGroup();
        modes.add(client);
        modes.add(server);
        modes.add(fat);
		modes.add(custom);

        JTextArea status = new JTextArea("Loading versions from the official NeoForge Maven repository...");
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);

        JButton browse = new JButton("Choose directory");
        JButton install = new JButton("Download and install");

        JLabel completion = new JLabel("Ready");
        completion.setForeground(new java.awt.Color(90, 90, 90));

        final File[] target = {new File(System.getProperty("user.dir"))};
        JTextField directory = new JTextField(target[0].getAbsolutePath());
        directory.setEditable(false);
        directory.setCaretPosition(0);

        JRadioButton customDirectory = new JRadioButton("Custom directory", true);
        JRadioButton launcherProfile = new JRadioButton("Launcher profile");
        ButtonGroup targetModes = new ButtonGroup();
        targetModes.add(customDirectory);
        targetModes.add(launcherProfile);

        JComboBox<String> profileType = new JComboBox<>(new String[] {"CurseForge", "Modrinth"});
        JComboBox<Path> profiles = new JComboBox<>();
        profiles.setEnabled(false);

        JButton chooseProfilesLocation = new JButton("Choose profiles location");
        JTextField profilesLocation = new JTextField();
        profilesLocation.setEditable(false);
        profilesLocation.setCaretPosition(0);

        Runnable refreshProfiles = () -> {
            LauncherProfileLocator.Type type = profileType.getSelectedIndex() == 0
                    ? LauncherProfileLocator.Type.CURSEFORGE : LauncherProfileLocator.Type.MODRINTH;
            profiles.removeAllItems();
            String location = profilesLocation.getText().trim();
            if (location.isEmpty()) {
                LauncherProfileLocator.findProfiles(type).forEach(profiles::addItem);
            } else {
                LauncherProfileLocator.findProfiles(Path.of(location)).forEach(profiles::addItem);
            }
            profiles.setEnabled(launcherProfile.isSelected());
            if (launcherProfile.isSelected() && profiles.getItemCount() > 0) {
                profiles.setSelectedIndex(0);
                target[0] = profiles.getItemAt(0).toFile();
                directory.setText(target[0].getAbsolutePath());
                directory.setCaretPosition(0);
            }
        };

        customDirectory.addActionListener(event -> {
            profiles.setEnabled(false);
            directory.setText(target[0].getAbsolutePath());
        });

        launcherProfile.addActionListener(event -> refreshProfiles.run());
        profileType.addActionListener(event -> refreshProfiles.run());

        chooseProfilesLocation.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(profilesLocation.getText().isBlank()
                    ? target[0] : new File(profilesLocation.getText()));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                profilesLocation.setText(chooser.getSelectedFile().getAbsolutePath());
                profilesLocation.setCaretPosition(0);
                refreshProfiles.run();
            }
        });

        profiles.addActionListener(event -> {
            Path selected = (Path) profiles.getSelectedItem();
            if (selected != null && launcherProfile.isSelected()) {
                target[0] = selected.toFile();
                directory.setText(target[0].getAbsolutePath());
                directory.setCaretPosition(0);
            }
        });

        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(target[0]);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                target[0] = chooser.getSelectedFile();
                directory.setText(target[0].getAbsolutePath());
                directory.setCaretPosition(0);
                if (launcherProfile.isSelected()) {
                    profiles.addItem(target[0].toPath());
                    profiles.setSelectedItem(target[0].toPath());
                } else {
                    customDirectory.setSelected(true);
                    profiles.setEnabled(false);
                }
            }
        });

        install.addActionListener(event -> {
            String version = (String) versions.getSelectedItem();
            if (version == null || version.startsWith("Loading")) {
                return;
            }

            if (launcherProfile.isSelected() && profiles.getSelectedItem() == null) {
                status.setText("No " + profileType.getSelectedItem() + " profiles were found. Choose a profile directory first.\n");
                completion.setText("Profile required");
                completion.setForeground(java.awt.Color.RED);
                return;
            }

            String action;
            if (client.isSelected()) {
                action = "--installClient";
            } else if (server.isSelected()) {
                action = "--installServer";
            } else {
                action = "--fat-installer";
            }
            install.setEnabled(false);

            completion.setText("Installing...");
            completion.setForeground(new java.awt.Color(180, 110, 0));
            status.setText("Downloading official NeoForge " + version + " installer...\n");

            new SwingWorker<Integer, String>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    File installerJar = OfficialInstallerLauncher.download(version);
                    publish("Downloaded official installer: " + installerJar + "\nStarting installation...\n");
                    File destination = action.equals("--fat-installer")
                            ? new File(target[0], "neoforge-" + version + "-fat.jar")
                            : target[0];
                    publish("Using destination: " + destination.getAbsolutePath() + "\n");
                    publish("Running official NeoForge installer...\n");
                    return runInstallation(installerJar, action, version, destination, line -> publish(line + "\n"));
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
                            completion.setForeground(new java.awt.Color(0, 128, 0));
                            install.setText("Install again");
                        } else {
                            status.append("Official installer exited with code " + result + ".\n");
                            completion.setText("Installation failed (exit code " + result + ")");
                            completion.setForeground(java.awt.Color.RED);
                        }

                    } catch (Exception exception) {
                        status.append("Installation failed: " + exception.getMessage() + "\n");
                        completion.setText("Installation failed");
                        completion.setForeground(java.awt.Color.RED);
                        JOptionPane.showMessageDialog(frame, exception.toString(), "Installation failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 18, 8, 18));

        JLabel versionLabel = new JLabel("NeoForge version:");
        versionLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        top.add(versionLabel);
        top.add(versions);
        top.add(Box.createVerticalStrut(5));

        JPanel modesPanel = new JPanel();
        modesPanel.setLayout(new BoxLayout(modesPanel, BoxLayout.Y_AXIS));
        modesPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        client.setMargin(new Insets(3, 3, 3, 3));
        server.setMargin(new Insets(3, 3, 3, 3));
        fat.setMargin(new Insets(3, 3, 3, 3));
        client.setIconTextGap(10);
        server.setIconTextGap(10);
        fat.setIconTextGap(10);
        modesPanel.add(client);
        modesPanel.add(server);
        modesPanel.add(fat);
		modesPanel.add(custom);
        top.add(modesPanel);
        top.add(Box.createVerticalStrut(8));

        JPanel targetModePanel = new JPanel();
        targetModePanel.setLayout(new BoxLayout(targetModePanel, BoxLayout.Y_AXIS));
        targetModePanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        customDirectory.setMargin(new Insets(3, 3, 3, 3));
        launcherProfile.setMargin(new Insets(3, 3, 3, 3));
        targetModePanel.add(customDirectory);
        targetModePanel.add(launcherProfile);

        JPanel profilePanel = new JPanel(new BorderLayout(5, 5));
        profilePanel.add(profileType, BorderLayout.WEST);
        profilePanel.add(profiles, BorderLayout.CENTER);
        targetModePanel.add(profilePanel);

        JPanel profilesLocationPanel = new JPanel(new BorderLayout(5, 5));
        profilesLocationPanel.add(profilesLocation, BorderLayout.CENTER);
        profilesLocationPanel.add(chooseProfilesLocation, BorderLayout.EAST);
        targetModePanel.add(profilesLocationPanel);
        top.add(targetModePanel);
        top.add(Box.createVerticalStrut(5));

        JPanel directoryPanel = new JPanel(new BorderLayout(5, 5));
        directoryPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        directoryPanel.add(directory, BorderLayout.CENTER);
        directoryPanel.add(browse, BorderLayout.EAST);
        top.add(directoryPanel);
        frame.add(top, BorderLayout.NORTH);
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 18, 8, 18));
        frame.add(new JScrollPane(status), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 18, 12, 18));
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(completion);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(install);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(560, 360));
        frame.pack();
        frame.setVisible(true);

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
                    status.setText("Select an action and directory, then download the official installer.\n");
                } catch (Exception exception) {
                    versions.removeAllItems();
                    status.setText("Could not load NeoForge versions: " + exception.getMessage());
                }
            }
        }.execute();
    }

}