/*
 ***************************************************************************************************
 * Copyright (c) 2017 Federal Institute for Risk Assessment (BfR), Germany
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors: Department Biological Safety - BfR
 *************************************************************************************************
 */
package de.bund.bfr.knime.fsklab.nodes;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ProgressMonitorInputStream;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import org.apache.commons.io.FileUtils;
import org.knime.core.node.DataAwareNodeDialogPane;
import org.knime.core.node.FlowVariableModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.FilesHistoryPanel;
import org.knime.core.node.util.FilesHistoryPanel.LocationValidation;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.util.FileUtil;
import de.bund.bfr.knime.fsklab.FskPortObject;
import de.bund.bfr.knime.fsklab.nodes.common.ui.FBrowseButton;

class FSKEditorJSNodeDialog extends DataAwareNodeDialogPane {

  private FSKEditorJSNodeSettings settings;
  private final FilesHistoryPanel m_readmePanel;
  private final FilesHistoryPanel m_workingDirectoryPanel;
  private final FlowVariableModel directoryVariable;
  private FileTableModel fileModel = new FileTableModel();
  private JTable fileTable = new JTable(fileModel);
  private JScrollPane centerPane;
  private JPanel container = new JPanel();
  private File currentWorkingDirectory;
  private WorkingDirectoryChangeListener changeListener = new WorkingDirectoryChangeListener();

  public FSKEditorJSNodeDialog() {
    settings = new FSKEditorJSNodeSettings();
    FlowVariableModel readmeVariable = createFlowVariableModel("readme", FlowVariable.Type.STRING);
    m_readmePanel =
        new FilesHistoryPanel(readmeVariable, "readme", LocationValidation.None, ".txt");
    directoryVariable = createFlowVariableModel("workingDirectory", FlowVariable.Type.STRING);
    m_workingDirectoryPanel =
        new FilesHistoryPanel(directoryVariable, "directory", LocationValidation.None);
    m_workingDirectoryPanel.getComponent(0).setEnabled(false);
    createUI();
  }

  /** Loads settings from saved settings. */

  @Override
  protected void loadSettingsFrom(NodeSettingsRO settings, PortObjectSpec[] specs)
      throws NotConfigurableException {
    try {
      this.settings.load(settings);

      // m_readmePanel.updateHistory();
      m_readmePanel.setSelectedFile(this.settings.getReadme());

      // m_workingDirectoryPanel.updateHistory();
      m_workingDirectoryPanel.setSelectedFile(this.settings.getWorkingDirectory());

      List<String> files;
      if (!m_workingDirectoryPanel.getSelectedFile().toString().equals("")) {
        try {
          URL url = FileUtil.toURL(m_workingDirectoryPanel.getSelectedFile().toString());
          Path localPath = FileUtil.resolveToPath(url);
          if (localPath != null) {
            files = Files.walk(localPath).filter(Files::isRegularFile).map(Path::toString)
                .collect(Collectors.toList());
            fileModel.filenames.clear();
            fileModel.filenames.addAll(files);
            fileTable.revalidate();
            currentWorkingDirectory = new File(this.settings.getWorkingDirectory());
          }

        } catch (IOException e1) {
          e1.printStackTrace();
        } catch (URISyntaxException e) {
          e.printStackTrace();
        }
      }
    } catch (InvalidSettingsException exception) {
      throw new NotConfigurableException(exception.getMessage(), exception);
    }
  }

  /** Loads settings from input port. */
  @Override
  protected void loadSettingsFrom(NodeSettingsRO settings, PortObject[] input)
      throws NotConfigurableException {

    final FSKEditorJSNodeSettings editorSettings = new FSKEditorJSNodeSettings();
    try {
      editorSettings.load(settings);
    } catch (InvalidSettingsException exception) {
      throw new NotConfigurableException("InvalidSettingsException", exception);
    }

    final FskPortObject inObj = (FskPortObject) input[0];

    /*
     * If input model has not changed (the original scripts stored in settings match the input
     * model).
     */
    if (Objects.equals(editorSettings.getWorkingDirectory(), inObj.getWorkingDirectory())) {
      // Updates settings
      this.settings = editorSettings;
    } else {
      // Discard settings and replace them with input model
      this.settings.setReadme(inObj.getReadme());
      this.settings.setWorkingDirectory(inObj.getWorkingDirectory());

    }
    if (!Objects.equals("", inObj.getReadme())) {
      m_readmePanel.getParent().setVisible(false);
    }
    m_readmePanel.updateHistory();
    m_readmePanel.setSelectedFile(this.settings.getReadme());
    m_workingDirectoryPanel.removeChangeListener(changeListener);
    m_workingDirectoryPanel.updateHistory();
    m_workingDirectoryPanel.setSelectedFile(this.settings.getWorkingDirectory());
    m_workingDirectoryPanel.addChangeListener(changeListener);

    List<String> files;
    try {
      URL url = FileUtil.toURL(m_workingDirectoryPanel.getSelectedFile().toString());
      Path localPath = FileUtil.resolveToPath(url);
      files = Files.walk(localPath).filter(Files::isRegularFile).map(Path::toString)
          .collect(Collectors.toList());
      fileModel.filenames.clear();
      fileModel.filenames.addAll(files);
      fileTable.revalidate();
      currentWorkingDirectory = new File(this.settings.getWorkingDirectory());
    } catch (IOException e1) {
      e1.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    this.settings = editorSettings;
  }

  @Override
  protected void saveSettingsTo(NodeSettingsWO settings) throws InvalidSettingsException {
    this.settings.setReadme(m_readmePanel.getSelectedFile().trim());
    m_readmePanel.addToHistory();

    this.settings.setWorkingDirectory(m_workingDirectoryPanel.getSelectedFile().trim());
    m_workingDirectoryPanel.addToHistory();

    String fileHolder = "";
    for (String oneFile : fileModel.filenames) {
      fileHolder = fileHolder + ";" + oneFile;
    }
    this.settings.setResources(fileHolder);

    this.settings.save(settings);
  }

  private void createUI() {

    final NodeContext nodeContext = NodeContext.getContext();
    final WorkflowManager wfm = nodeContext.getWorkflowManager();
    final WorkflowContext workflowContext = wfm.getContext();

    final JPanel readmePanel = new JPanel(new BorderLayout());
    readmePanel
        .setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Readme:"));
    readmePanel.add(m_readmePanel, BorderLayout.NORTH);
    readmePanel.add(Box.createHorizontalGlue());


    final JPanel workingDirectoryPanel = new JPanel(new BorderLayout());
    workingDirectoryPanel.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Working directory:"));
    workingDirectoryPanel.add(m_workingDirectoryPanel, BorderLayout.NORTH);
    workingDirectoryPanel.add(Box.createHorizontalGlue());

    m_workingDirectoryPanel.addChangeListener(changeListener);


    FBrowseButton resourcesButton = new FBrowseButton("Browse");
    resourcesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        JFileChooser fc = new JFileChooser();

        fc.setMultiSelectionEnabled(true);
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int response = fc.showOpenDialog(resourcesButton);
        if (response == JFileChooser.APPROVE_OPTION) {

          File[] files = fc.getSelectedFiles();

          if (m_workingDirectoryPanel.getSelectedFile().trim() != null
              && !m_workingDirectoryPanel.getSelectedFile().trim().equals("")) {
            try {
              currentWorkingDirectory = FileUtil
                  .getFileFromURL(FileUtil.toURL(m_workingDirectoryPanel.getSelectedFile().trim()));
            } catch (InvalidPathException | MalformedURLException e1) {
              e1.printStackTrace();
            }
          } else {
            currentWorkingDirectory = new File(workflowContext.getCurrentLocation(),
                nodeContext.getNodeContainer().getNameWithID().toString().replaceAll("\\W", "")
                    .replace(" ", "") + "_" + "workingDirectory"
                    + FSKEditorJSNodeModel.TEMP_DIR_UNIFIER.getAndIncrement());
            currentWorkingDirectory.mkdir();
            m_workingDirectoryPanel.setSelectedFile(currentWorkingDirectory.toString());
          }
          for (File oneFile : files) {
            SwingWorker worker = new SwingWorker() {
              @Override
              protected Object doInBackground() throws Exception {
                resourcesButton.setEnabled(false);
                String toPath = currentWorkingDirectory.toPath().toString() + File.separator
                    + oneFile.getName();
                copyFile(oneFile, toPath);
                return null;
              }

              @Override
              protected void done() {
                resourcesButton.setEnabled(true);
              }

            };

            worker.execute();



          }
          fileModel.filenames.addAll(
              Arrays.stream(files).map(p -> p.toPath().toString()).collect(Collectors.toList()));

          fileTable.revalidate();
        }
      }
    });



    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.add(readmePanel);
    container.add(workingDirectoryPanel);
    JPanel fileSelector = new JPanel(new BorderLayout());

    TitledBorder border = new TitledBorder("Select Resource(s) - to move to the working directory");
    border.setTitleJustification(TitledBorder.CENTER);
    border.setTitlePosition(TitledBorder.TOP);

    centerPane = new JScrollPane(fileTable);

    fileSelector.add(resourcesButton, BorderLayout.NORTH);
    fileSelector.add(centerPane, BorderLayout.CENTER);

    fileSelector.setBorder(border);

    container.add(fileSelector);

    addTab("Options", container);
  }

  public void changeWorkingDirectory(String selectedDirectory) {

    int choise = JOptionPane.showConfirmDialog(null, "Working directory is already set to "
        + currentWorkingDirectory + " \nAre you sure you want to change?");
    switch (choise) {
      case 0:
        try {
          URL url = FileUtil.toURL(currentWorkingDirectory.toString());
          Path localPath = FileUtil.resolveToPath(url);
          Files.list(localPath).forEach(path -> {
            try {
              File currentFile = path.toFile();
              if (currentFile.isDirectory()) {
                FileUtils.copyDirectoryToDirectory(currentFile, new File(selectedDirectory));
              } else {
                FileUtils.copyFileToDirectory(currentFile, new File(selectedDirectory));
              }

            } catch (IOException e) {
              e.printStackTrace();
            }
          });
        } catch (IOException | URISyntaxException e1) {
          e1.printStackTrace();
        }

        break;
      default:
        break;

    }
  }

  private void copyFile(File file, String toPath) {
    BufferedInputStream bis;
    BufferedOutputStream baos;
    try {
      bis = new BufferedInputStream(new FileInputStream(file));
      ProgressMonitorInputStream pmis =
          new ProgressMonitorInputStream(null, "Reading... " + file.getAbsolutePath(), bis);

      pmis.getProgressMonitor().setMillisToPopup(10);

      baos = new BufferedOutputStream(new FileOutputStream(toPath));

      byte[] buffer = new byte[2048];
      int nRead = 0;

      while ((nRead = pmis.read(buffer)) != -1) {
        baos.write(buffer, 0, nRead);
      }

      pmis.close();
      baos.flush();
      baos.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  class FileTableModel extends AbstractTableModel {
    protected List<String> filenames;

    protected String[] columnNames =
        new String[] {"name", "size", "last modified", "readable?", "writable?"};

    protected Class[] columnClasses =
        new Class[] {String.class, Long.class, Date.class, Boolean.class, Boolean.class};

    // This table model works for any one given directory
    public FileTableModel() {
      filenames = new ArrayList<String>();
    }

    public FileTableModel(List<String> files) {
      this.filenames = files; // Store a list of files in the directory
    }

    // These are easy methods.
    public int getColumnCount() {
      return 5;
    } // A constant for this model

    public int getRowCount() {
      return filenames.size();
    } // # of files in dir

    // Information about each column.
    public String getColumnName(int col) {
      return columnNames[col];
    }

    public Class getColumnClass(int col) {
      return columnClasses[col];
    }


    // The method that must actually return the value of each cell.
    public Object getValueAt(int row, int col) {
      File f = new File(filenames.get(row));
      switch (col) {
        case 0:
          return new File(filenames.get(row)).getName();
        case 1:
          return new Long(f.length());
        case 2:
          return new Date(f.lastModified());
        case 3:
          return f.canRead() ? Boolean.TRUE : Boolean.FALSE;
        case 4:
          return f.canWrite() ? Boolean.TRUE : Boolean.FALSE;
        default:
          return null;
      }
    }
  }
  private class WorkingDirectoryChangeListener implements ChangeListener {

    @Override
    public void stateChanged(ChangeEvent e) {
      m_workingDirectoryPanel.getComponent(0).setEnabled(false);
      String selectedFile = m_workingDirectoryPanel.getSelectedFile();
      try {
        if (selectedFile != null && !selectedFile.equals("")) {
          String modifiedSelectedDirectory =
              FileUtil.getFileFromURL(FileUtil.toURL(selectedFile)).toString();
          File sfile = new File(modifiedSelectedDirectory);
          if (!sfile.exists()) {
            sfile.mkdir();
          }
          if (currentWorkingDirectory != null
              && !modifiedSelectedDirectory.equals(currentWorkingDirectory.toString())) {
            changeWorkingDirectory(modifiedSelectedDirectory);
            currentWorkingDirectory = sfile;
          }
          List<String> files;
          try {

            files = Files.walk(Paths.get(modifiedSelectedDirectory)).filter(Files::isRegularFile)
                .map(Path::toString).collect(Collectors.toList());
            fileModel.filenames.clear();
            fileModel.filenames.addAll(files);
            fileTable.revalidate();
            currentWorkingDirectory = sfile;
          } catch (IOException e1) {
            e1.printStackTrace();
          }
        }
      } catch (InvalidPathException | MalformedURLException e1) {
        e1.printStackTrace();
      }

    }
  }
}
