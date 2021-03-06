/*
 ***************************************************************************************************
 * Copyright (c) 2018 Federal Institute for Risk Assessment (BfR), Germany
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

import java.io.IOException;
import java.util.List;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bund.bfr.knime.fsklab.SimulationEntity;

public class SimulatorNodeSettings {

  // Configuration keys
  private static final String CFG_METADATA = "metaData";
  private static final String CFG_SIMULATION_LIST = "simulations";

  // Setting models
  private static NodeLogger LOGGER = NodeLogger.getLogger("SimulatorNodeSettings");

  private static final String CFG_Model_Script = "ModelScript";
  

  private String modelStript;
  private List<SimulationEntity> listOfSimulation;

  void saveSettings(final NodeSettingsWO settings) {

    final ObjectMapper objectMapper = new ObjectMapper();

    // save model Script
    if (modelStript != null) {
      settings.addString( CFG_Model_Script, modelStript);
    }
    if (listOfSimulation != null && listOfSimulation.size() > 0) {
      try {
        String stringVal = objectMapper.writeValueAsString(listOfSimulation);
        settings.addString(CFG_SIMULATION_LIST, stringVal);
      } catch (JsonProcessingException exception) {
        LOGGER.warn("Error saving meta data", exception);
      }
    }
  }

  /**
   * Loads the settings from the given node settings object.
   * 
   * @param settings a node settings object.
   * @throws InvalidSettingsException
   * @throws IOException
   * @throws JsonMappingException
   * @throws JsonParseException
   */
  void loadSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

    final ObjectMapper objectMapper = new ObjectMapper();

    // load meta data
    if (settings.containsKey(CFG_Model_Script)) {
      modelStript = settings.getString(CFG_Model_Script);
    }
    
    if (settings.containsKey(CFG_SIMULATION_LIST)) {
      final String stringVal = settings.getString(CFG_SIMULATION_LIST);

      try {
        listOfSimulation = (List<SimulationEntity>) objectMapper.readValue(stringVal,
            new TypeReference<List<SimulationEntity>>() {});
      } catch (IOException e) {
        throw new InvalidSettingsException(e);
      }
    }

    // Uses empty array if CFG_RESOURCES is missing (in case of old nodes).
  }
 

  public List<SimulationEntity> getListOfSimulation() {
    return listOfSimulation;
  }

  public void setListOfSimulation(List<SimulationEntity> listOfSimulation) {
    this.listOfSimulation = listOfSimulation;
  }
  
  public String getModelStript() {
    return modelStript;
  }

  public void setModelStript(String modelStript) {
    this.modelStript = modelStript;
  }
}
