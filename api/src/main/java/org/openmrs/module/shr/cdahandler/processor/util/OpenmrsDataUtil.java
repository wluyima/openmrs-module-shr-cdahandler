package org.openmrs.module.shr.cdahandler.processor.util;

import java.io.ByteArrayInputStream;
import java.text.ParseException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.marc.everest.datatypes.ANY;
import org.marc.everest.datatypes.BL;
import org.marc.everest.datatypes.ED;
import org.marc.everest.datatypes.INT;
import org.marc.everest.datatypes.MO;
import org.marc.everest.datatypes.PQ;
import org.marc.everest.datatypes.SD;
import org.marc.everest.datatypes.ST;
import org.marc.everest.datatypes.TS;
import org.marc.everest.datatypes.generic.CE;
import org.marc.everest.datatypes.generic.CV;
import org.marc.everest.datatypes.generic.RTO;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.cdahandler.configuration.CdaHandlerConfiguration;
import org.openmrs.module.shr.cdahandler.exception.DocumentImportException;
import org.openmrs.obs.ComplexData;

/**
 * Data utilities for OpenMRS
 */
public final class OpenmrsDataUtil {
	
	// Log
	protected final Log log = LogFactory.getLog(this.getClass());

	// singleton instance
	private static OpenmrsDataUtil s_instance;
	private static Object s_lockObject = new Object();

	// Util classes
	private final CdaHandlerConfiguration m_configuration = CdaHandlerConfiguration.getInstance();
	private final OpenmrsConceptUtil m_conceptUtil = OpenmrsConceptUtil.getInstance();
	
	/**
	 * Private ctor
	 */
	protected OpenmrsDataUtil()
	{
	}
	
	/**
	 * Get the singleton instance
	 */
	public static OpenmrsDataUtil getInstance()
	{
		if(s_instance == null)
		{
			synchronized (s_lockObject) {
				if(s_instance == null) // Another thread might have created while we were waiting for a lock
					s_instance = new OpenmrsDataUtil();
			}
		}
		return s_instance;
	}

	
	/**
	 * Creates a simple observation representing an RMIM type with type and value 
	 * @throws DocumentImportException 
	 * @throws ParseException 
	 */
	public Obs getRmimValueObservation(String code, TS date, ANY value) throws DocumentImportException {

		Obs res = new Obs();
		
		// Set concept
		Concept concept = this.m_conceptUtil.getOrCreateRMIMConcept(code, value);
		if(!concept.getDatatype().equals(this.m_conceptUtil.getConceptDatatype(value)))
			throw new DocumentImportException("Cannot store the specified type of data in the concept field");
		res.setConcept(concept);
		// Set date
		res.setObsDatetime(date.getDateValue().getTime());
		res = this.setObsValue(res, value);
		// return back to the caller for further modification
		return res;
    }
	
	/**
	 * Set the observation value using an appropriate call
	 * @throws ParseException 
	 * @throws DocumentImportException 
	 */
	public Obs setObsValue(Obs observation, ANY value) throws DocumentImportException
	{
		// TODO: PQ should technically be a numeric with unit ... hmm...
		if(value instanceof PQ)
		{
			PQ pqValue = (PQ)value;
			ConceptNumeric conceptNumeric = Context.getConceptService().getConceptNumeric(observation.getConcept().getId());
			String conceptUnits = this.m_conceptUtil.getUcumUnitCode(conceptNumeric);
			if(!conceptUnits.equals(pqValue.getUnit()))
				pqValue = pqValue.convert(conceptUnits);
			log.debug(String.format("Storing value '%s' (original: '%s') to match concept type", pqValue, value));
			observation.setValueNumeric(pqValue.toDouble());
			
		}
		else if(value instanceof RTO || value instanceof MO)
			observation.setValueText(value.toString());
		else if(value instanceof INT)
			observation.setValueNumeric(((INT) value).toDouble());
		else if(value instanceof TS)
			observation.setValueDatetime(((TS) value).getDateValue().getTime());
		else if(value instanceof ST)
			observation.setValueText(value.toString());
		else if(value instanceof BL)
			observation.setValueBoolean(((BL)value).toBoolean());
		else if(value instanceof ED)
		{
			ByteArrayInputStream textStream = new ByteArrayInputStream(((ED) value).getData());
			ComplexData complexData = new ComplexData("observationdata", textStream);
			observation.setComplexData(complexData);
		}
		else if(value instanceof SD)
		{
			ByteArrayInputStream textStream = new ByteArrayInputStream(((SD) value).toString().getBytes());
			ComplexData complexData = new ComplexData("observationdata", textStream);
			observation.setComplexData(complexData);
		}
		else if(value instanceof CE)
		{
			
			// Set code system if possible... 
			// Is the value an OpenMRS concept
			Concept concept = this.m_conceptUtil.getTypeSpecificConcept((CE<String>)value, null);
			if(concept == null) // Maybe an inappropriate concept then?
				concept = this.m_conceptUtil.getOrCreateConcept((CV<String>)value);
			
			this.m_conceptUtil.addAnswerToConcept(observation.getConcept(), concept);
			observation.setValueCoded(concept);
				
		}
		else
			throw new DocumentImportException("Cannot represent this concept!");
				
		return observation;
	}


}