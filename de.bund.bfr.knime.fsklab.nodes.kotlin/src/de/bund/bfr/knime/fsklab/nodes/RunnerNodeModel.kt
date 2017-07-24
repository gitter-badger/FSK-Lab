package de.bund.bfr.knime.fsklab.nodes

import com.sun.jna.Platform
import de.bund.bfr.knime.fsklab.FskPortObject
import de.bund.bfr.knime.fsklab.FskPortObjectSpec
import de.bund.bfr.knime.fsklab.nodes.controller.IRController.RException
import de.bund.bfr.knime.fsklab.nodes.controller.LibRegistry
import de.bund.bfr.knime.fsklab.nodes.controller.RController
import de.bund.bfr.rakip.generic.ParameterClassification
import org.knime.core.data.image.png.PNGImageContent
import org.knime.core.node.ExecutionContext
import org.knime.core.node.ExecutionMonitor
import org.knime.core.node.NodeLogger
import org.knime.core.node.NodeModel
import org.knime.core.node.NodeSettingsRO
import org.knime.core.node.NodeSettingsWO
import org.knime.core.node.port.PortObject
import org.knime.core.node.port.PortObjectSpec
import org.knime.core.node.port.image.ImagePortObject
import org.knime.core.node.port.image.ImagePortObjectSpec
import org.knime.core.util.FileUtil
import java.awt.Image
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

private val LOGGER = NodeLogger.getLogger("Runner node")

// Output for an FSK object
private val FSK_SPEC = FskPortObjectSpec.INSTANCE

// Output for a PNG image
private val PNG_SPEC = ImagePortObjectSpec(PNGImageContent.TYPE)

// Input and output types
private val IN_TYPES = arrayOf(FskPortObject.TYPE)
private val OUT_TYPES = arrayOf(FskPortObject.TYPE, ImagePortObject.TYPE_OPTIONAL)

class RunnerNodeModel : NodeModel(IN_TYPES, OUT_TYPES) {

	private val settings = RunnerNodeSettings()

	// --- internal settings methods ---
	override fun loadInternals(nodeInternDir: File, exec: ExecutionMonitor) {
		InternalSettings.loadInternals(nodeInternDir)
	}

	override fun saveInternals(nodeInternDir: File, exec: ExecutionMonitor) {
		InternalSettings.saveInternals(nodeInternDir)
	}

	override fun reset() = InternalSettings.reset()

	// --- node settings methods ---
	override fun saveSettingsTo(settings: NodeSettingsWO) = this.settings.saveSettingsTo(settings)

	override fun validateSettings(settings: NodeSettingsRO) = this.settings.validateSettings(settings)
	override fun loadValidatedSettingsFrom(settings: NodeSettingsRO) = this.settings.loadValidatedSettingsFrom(settings)

	override fun configure(inSpecs: Array<PortObjectSpec>) = arrayOf(FSK_SPEC, PNG_SPEC)

	override fun execute(inObjects: Array<PortObject>, exec: ExecutionContext): Array<PortObject> {

		var fskObj = inObjects[0] as FskPortObject

		var newScript = ""
		var onError = false

		val indepVars = fskObj.genericModel.modelMath?.parameter
				?.filter { it.classification == ParameterClassification.input }?.toList() ?: emptyList()

		for (indepVar in indepVars) {
			if (indepVar.name.isNullOrBlank() || indepVar.value.isNullOrBlank()) {
				onError = true
				break
			}
			newScript += "$indepVar.name <- $indepVar.value\n"
		}

		if (!onError) fskObj.param = newScript

		RController().use { controller -> fskObj = runSnippet(controller, fskObj) }

		var stream: FileInputStream? = null
		return try {
			stream = FileInputStream(InternalSettings.imgFile)
			val content = PNGImageContent(stream)
			InternalSettings.plot = content.image
			arrayOf(fskObj, ImagePortObject(content, PNG_SPEC))
		} catch (_: IOException) {
			LOGGER.warn("There is no image created")
			arrayOf(fskObj)
		} finally {
			stream?.close()
		}
	}
	
	fun getResultImage() = InternalSettings.plot

	private fun runSnippet(controller: RController, fskObj: FskPortObject): FskPortObject {

		// Add path
		val libRegistry = LibRegistry.instance()
		val installationPath = libRegistry.installationPath.toString().replace("\\", "/")
		val cmd = ".libPaths(c('$installationPath', .libPaths()))"
		val newPaths = controller.eval(cmd).asStrings()

		// Run model
		controller.eval(fskObj.param + "\n" + fskObj.model)

		// Save workspace
		if (fskObj.workspace == null)
			fskObj.workspace = FileUtil.createTempFile("workspace", ".R")
		val workspacePath = fskObj.workspace!!.absolutePath.replace("\\", "/")
		controller.eval("save.image('$workspacePath')")

		// Creates chart into m_imageFile
		try {
			val cc = ChartCreator(controller)
			cc.plot(InternalSettings.imgFile.absolutePath.replace("\\", "/"), fskObj.viz)
		} catch (_: RException) {
			LOGGER.warn("Visualization script failed")
		}

		// Restore .libPaths() to the original library path which is in the last position
		controller.eval(".libPaths()[$newPaths.size]")

		return fskObj
	}

	private inner class ChartCreator(val controller: RController) {

		init {
			// initialize necessary R stuff to plot
			if (Platform.isMac()) {
				controller.eval("library('Cairo')");
				controller.eval("options(device='png', bitmapType='cairo')");
			} else {
				controller.eval("options(device='png')");
			}
		}

		fun plot(path: String, vizScript: String) {

			// gets values
			val width = settings.widthModel.intValue
			val height = settings.heightModel.intValue
			val res = settings.resolutionModel.stringValue
			val textPointSize = settings.textPointSizeModel.intValue
			val colour = settings.colourModel.colorValue

			val hexColour = String.format("#%02x%02x%02x", colour.red, colour.green, colour.blue)
			
			val pngCommand = "png(path='$path', width='$width', height='$height', " +
					"pointSize='$textPointSize', bg='$hexColour', res='$res')"
			controller.eval(pngCommand)
			
			controller.eval(vizScript)
			controller.eval("dev.off()")		
		}
	}

	/**
	 * @property imgFile image file to use for this current node. Initialized to temp location.
	 */
	private object InternalSettings {

		val FILE_NAME = "Rplot"
		val imgFile: File
		var plot: Image? = null

		// TODO: throws IOException (move to docstring)
		init {
			imgFile = FileUtil.createTempFile("RunnerNode-", ".png")
		}

		/**
		 * Loads the saved image.
		 * @throws IOException
		 */
		fun loadInternals(nodeInternDir: File) {
			val file = File(nodeInternDir, FILE_NAME + ".png")

			if (file.exists() && file.canRead()) {
				FileUtil.copy(file, imgFile)
				FileInputStream(imgFile).use { stream -> plot = PNGImageContent(stream).getImage() }
			}
		}

		/**
		 * Saves the saved image.
		 * @throws IOException
		 */
		fun saveInternals(nodeInternDir: File) {
			if (plot != null) {
				val file = File(nodeInternDir, FILE_NAME + ".png")
				FileUtil.copy(imgFile, file)
			}
		}

		/**
		 * Clear the contents of the image file.
		 * @throws IOException
		 */
		fun reset() {
			plot = null
			FileOutputStream(imgFile).use { erasor -> erasor.write("".toByteArray()) }
		}
	}
}