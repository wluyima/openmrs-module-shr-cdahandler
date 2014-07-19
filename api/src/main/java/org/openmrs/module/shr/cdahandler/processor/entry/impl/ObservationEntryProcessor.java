package org.openmrs.module.shr.cdahandler.processor.entry.impl;

import java.util.List;

import org.marc.everest.datatypes.ANY;
import org.marc.everest.datatypes.BL;
import org.marc.everest.datatypes.II;
import org.marc.everest.datatypes.doc.StructDocNode;
import org.marc.everest.datatypes.generic.CE;
import org.marc.everest.datatypes.generic.CV;
import org.marc.everest.interfaces.IGraphable;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ClinicalStatement;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Observation;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Section;
import org.marc.everest.rmim.uv.cdar2.vocabulary.ObservationInterpretation;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.cdahandler.CdaHandlerConstants;
import org.openmrs.module.shr.cdahandler.exception.DocumentImportException;
import org.openmrs.module.shr.cdahandler.exception.DocumentValidationException;
import org.openmrs.module.shr.cdahandler.exception.ValidationIssueCollection;
import org.openmrs.module.shr.cdahandler.processor.context.ProcessorContext;
import org.openmrs.module.shr.cdahandler.processor.section.SectionProcessor;
import org.openmrs.module.shr.cdahandler.processor.util.OpenmrsMetadataUtil;


/**
 * Represents a processor for Observation Entries
 */
public abstract class ObservationEntryProcessor extends EntryProcessorImpl {

	// Metadata util
	protected final OpenmrsMetadataUtil m_metaDataUtil = OpenmrsMetadataUtil.getInstance();

	/**
	 * Process the observation
	 * @see org.openmrs.module.shr.cdahandler.processor.entry.impl.EntryProcessorImpl#process(org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ClinicalStatement)
	 */
	@Override
	public BaseOpenmrsData process(ClinicalStatement entry) throws DocumentImportException {
		
		// Validate
		if(this.m_configuration.getValidationEnabled())
		{
			ValidationIssueCollection issues = this.validate(entry);
			if(issues.hasErrors())
				throw new DocumentValidationException(entry, issues);
		}

		// Observation from entry
		Observation observation = (Observation)entry;
		
		// Create concept and datatype services
		Encounter encounterInfo = (Encounter)this.getEncounterContext().getParsedObject();
		Obs parentObs = (Obs)this.getContext().getParsedObject();
		
		// TODO: Get an existing obs and do an update to the obs? or void it because the new encounter supersedes it..
		// Void any existing obs that have the same id
		Obs previousObs = null;
		if(observation.getId() != null)
			for(Obs currentObs : Context.getObsService().getObservationsByPerson(encounterInfo.getPatient()))
			{
				for(II id : observation.getId())
					if(previousObs == null && currentObs.getAccessionNumber() != null
					&& currentObs.getAccessionNumber().equals(this.m_datatypeUtil.formatIdentifier(id)))
					{
						previousObs = currentObs;
						Context.getObsService().voidObs(currentObs, String.format("replaced in %s", encounterInfo));
					}
			}
		
		
		Obs res = new Obs();
		res.setPreviousVersion(previousObs);
		res.setObsGroup(parentObs);
		// Now ... Get the encounter and copy cascade what can be cascaded
		res.setPerson(encounterInfo.getPatient());
		res.setLocation(encounterInfo.getLocation());
		res.setDateCreated(encounterInfo.getDateCreated());
		res.setEncounter(encounterInfo);
		
		if(observation.getId() != null && !observation.getId().isNull())
			res.setAccessionNumber(this.m_datatypeUtil.formatIdentifier(observation.getId().get(0)));
		
		// Value may be changed
		ANY value = observation.getValue();
		// Code 
		Concept concept = this.m_conceptUtil.getTypeSpecificConcept(observation.getCode(), observation.getValue());
		if(concept == null && observation.getValue() != null && this.m_configuration.getAutoCreateConcepts())
		{
			concept = this.m_conceptUtil.createConcept(observation.getCode(), observation.getValue());
			// Try to add this concept as a valid set member of the context
			this.m_conceptUtil.addConceptToSet(parentObs.getConcept(), concept);
		}
		else if(concept == null)
			throw new DocumentImportException(String.format("Cannot reliably establish the type of observation concept to create based on code %s", observation.getCode()));
		else if(concept.getDatatype().getUuid().equals(ConceptDatatype.N_A_UUID) && 
				(observation.getValue() == null || 
				observation.getValue() instanceof CV || 
				observation.getValue() instanceof BL)) // NA is an indicator .. We need to figure out what the question was?
		{
			// Is this really an indicator that is enabled?
			if(BL.FALSE.equals(observation.getValue()))
				return null; // Don't save this .. bail out!
			
			// Get potential question
			List<Concept> questions = Context.getConceptService().getConceptsByAnswer(concept);
			value = observation.getCode();
			if(questions.size() == 1)// There is only one question so we set the value
				concept = questions.get(0);
			else // Create a question indicator
				concept = this.m_conceptUtil.getOrCreateRMIMConcept(concept.getDisplayString() + " Indicator", value);
			
			
		}
		else if(!concept.getDatatype().equals(this.m_conceptUtil.getConceptDatatype(observation.getValue())))
			throw new DocumentImportException(String.format("Cannot store data of type %s in a concept field assigned %s", this.m_conceptUtil.getConceptDatatype(observation.getValue()).getName(), concept.getDatatype().getName()));
		
		res.setConcept(concept);
		
		// Effective time is value
		if(observation.getEffectiveTime() != null)
		{
			if(observation.getEffectiveTime().getValue() != null && !observation.getEffectiveTime().getValue().isNull())
				res.setObsDatetime(observation.getEffectiveTime().getValue().getDateValue().getTime());
			else if(observation.getEffectiveTime().getLow() != null)
			{
				String comment = "";
				// TODO: How to more elegantly handle this?
				if(observation.getEffectiveTime().getLow() != null && !observation.getEffectiveTime().getLow().isNull())
				{
					res.setObsDatetime(observation.getEffectiveTime().getLow().getDateValue().getTime());
					comment += String.format("From %s ", observation.getEffectiveTime().getLow().getDateValue().getTime());
				}
				if(observation.getEffectiveTime().getHigh() != null && !observation.getEffectiveTime().getHigh().isNull())
					comment += String.format("Until %s ", observation.getEffectiveTime().getHigh().getDateValue().getTime());
				res.setComment(comment);
			}
		}
		else
			res.setObsDatetime(encounterInfo.getEncounterDatetime());

		if(res.getObsDatetime() == null)
			res.setObsDatetime(encounterInfo.getEncounterDatetime());

		// Comments?
		if(observation.getText() != null)
		{
			if(observation.getText().getReference() != null) // Reference
			{
				ProcessorContext sectionContext = this.getContext();
				while(!(sectionContext.getRawObject() instanceof Section))
					sectionContext = sectionContext.getParent();
				
				// Now find the text
				StructDocNode referencedNode = ((Section)sectionContext.getRawObject()).getText().findNodeById(observation.getText().getReference().getValue());
				if(referencedNode != null)
				{
					res.setComment(referencedNode.toString());
				}
			}
		}

		
		// Get entry value
		res = this.m_dataUtil.setObsValue(res, value);
		res = Context.getObsService().saveObs(res, null);
		    
		
		// Repeat number ... as an obs.. 
		if(observation.getRepeatNumber() != null && observation.getRepeatNumber().getValue() != null)
		{
			Obs repeatObs = this.m_dataUtil.getRmimValueObservation(this.m_conceptUtil.getLocalizedString("obs.repeatNumber"), observation.getEffectiveTime().getValue(), observation.getRepeatNumber().getValue());
			repeatObs.setPerson(res.getPerson());
			repeatObs.setLocation(res.getLocation());
			repeatObs.setEncounter(res.getEncounter());
			repeatObs.setObsGroup(res);
			repeatObs.setDateCreated(res.getDateCreated());
			Context.getObsService().saveObs(repeatObs, null);
		}
		
		// Method
		if(observation.getMethodCode() != null)
			for(CE<String> methodCode : observation.getMethodCode())
			{
				Obs methodObs = this.m_dataUtil.getRmimValueObservation(this.m_conceptUtil.getLocalizedString("obs.methodCode"), observation.getEffectiveTime().getValue(), methodCode);
				methodObs.setPerson(res.getPerson());
				methodObs.setLocation(res.getLocation());
				methodObs.setEncounter(res.getEncounter());
				methodObs.setDateCreated(res.getDateCreated());
				methodObs.setObsGroup(res);
				Context.getObsService().saveObs(methodObs, null);
			}

		// Interpretation
		if(observation.getInterpretationCode() != null)
			for(CE<ObservationInterpretation> interpretationCode : observation.getInterpretationCode())
			{
				Obs interpretationObs = this.m_dataUtil.getRmimValueObservation(this.m_conceptUtil.getLocalizedString("obs.interpretationCode"), observation.getEffectiveTime().getValue(), interpretationCode);
				interpretationObs.setPerson(res.getPerson());
				interpretationObs.setLocation(res.getLocation());
				interpretationObs.setEncounter(res.getEncounter());
				interpretationObs.setObsGroup(res);
				interpretationObs.setDateCreated(res.getDateCreated());
				Context.getObsService().saveObs(interpretationObs, null);
			}

		// Process any components
		ProcessorContext childContext = new ProcessorContext(observation, res, this);
		super.processEntryRelationships(entry, childContext);
		
		return res;
	}




	/**
	 * Gets the code expected to be on this observation (null if none specified)
	 * @return
	 */
	protected abstract CE<String> getExpectedCode();
	
	/**
	 * Validate the observation
	 */
	@Override
    public ValidationIssueCollection validate(IGraphable object) {
		
		ValidationIssueCollection validationIssues = super.validate(object);
		if(validationIssues.hasErrors()) return validationIssues;
		
		// Validate now
		ClinicalStatement statement = (ClinicalStatement)object;
		// Must be an observation
		if(!statement.isPOCD_MT000040UVObservation())
			validationIssues.error(super.getInvalidClinicalStatementErrorText(Observation.class, statement.getClass()));

		Observation obs = (Observation)statement;
		CE<String> expectedCode = this.getExpectedCode();
		if(expectedCode != null && (obs.getCode() == null || !obs.getCode().semanticEquals(expectedCode).toBoolean()))
			validationIssues.warn(String.format("Observation carries code %s but expected %s", obs.getCode(), expectedCode.getCode()));
		else if(expectedCode != null && obs.getCode().getDisplayName() == null)
			obs.getCode().setDisplayName(expectedCode.getDisplayName());
		if(obs.getCode() == null)
			validationIssues.error("All observations must have a code to be imported");
		if(obs.getValue() instanceof CE && ((CE)obs.getValue()).isNull() )
			validationIssues.error("In order to persist coded values the value must be populated with data");
			
		
		if(obs.getInterpretationCode() != null)
			for(CE<ObservationInterpretation> code : obs.getInterpretationCode())
				if(code.getCode() != null && code.getCode().getCodeSystem() == null)
					validationIssues.error("Interpretation code must be drawn from ObservationInterpretation");
		if(obs.getMethodCode() != null)
			for(CE<String> code : obs.getMethodCode())
				if(code.getCode() != null && code.getCodeSystem() == null)
					validationIssues.error("MethodCode must specify the valuset from which it was drawn via CodeSystem attribute");
		
		
		return validationIssues;
	}
}