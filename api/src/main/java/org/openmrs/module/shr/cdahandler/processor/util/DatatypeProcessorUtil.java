package org.openmrs.module.shr.cdahandler.processor.util;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.marc.everest.annotations.Properties;
import org.marc.everest.annotations.Property;
import org.marc.everest.datatypes.AD;
import org.marc.everest.datatypes.ADXP;
import org.marc.everest.datatypes.EN;
import org.marc.everest.datatypes.ENXP;
import org.marc.everest.datatypes.II;
import org.marc.everest.datatypes.TS;
import org.marc.everest.datatypes.generic.CS;
import org.marc.everest.datatypes.generic.CV;
import org.marc.everest.datatypes.generic.IVL;
import org.marc.everest.formatters.FormatterElementContext;
import org.marc.everest.interfaces.IEnumeratedVocabulary;
import org.marc.everest.interfaces.IGraphable;
import org.marc.everest.rmim.uv.cdar2.rim.InfrastructureRoot;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.module.shr.cdahandler.configuration.CdaHandlerConfiguration;
import org.openmrs.module.shr.cdahandler.exception.DocumentImportException;

/**
 * A class containing utilities for parsing v3 datatypes
 * @author Justin
 *
 */
public final class DatatypeProcessorUtil {

	// singleton instance
	private static DatatypeProcessorUtil s_instance;
	private static Object s_lockObject = new Object();

	// Identifier format
	private CdaHandlerConfiguration m_configuration;

	/**
	 * Private ctor
	 */
	private DatatypeProcessorUtil()
	{
		
	}
	
	/**
	 * Get the singleton instance
	 */
	public static DatatypeProcessorUtil getInstance()
	{
		if(s_instance == null)
		{
			synchronized (s_lockObject) {
				if(s_instance == null) // Another thread might have created while we were waiting for a lock
				{
					s_instance = new DatatypeProcessorUtil();
					s_instance.m_configuration = CdaHandlerConfiguration.getInstance();
				}
			}
		}
		return s_instance;
	}

	/**
	 * Format an ID into a string
	 * @return
	 */
	public String formatIdentifier(II id)
	{
		if(id == null) return "";
		return String.format(this.m_configuration.getIdFormat(), id.getRoot(), id.getExtension());
	}
	
	/**
	 * Format a code
	 * @param code The code to format
	 * @return The formatted code identifier
	 */
	public String formatSimpleCode(CS<? extends IEnumeratedVocabulary> code)
	{
		if(code == null || code.getCode() == null) return "";
		return String.format(this.m_configuration.getIdFormat(), code.getCode().getCodeSystem(), code.getCode().getCode());
	}
	
	/**
	 * Format a coded value
	 * @param code The code to format
	 * @return The formatted code identifier
	 */
	public String formatCodeValue(CV<?> code)
	{
		if(code == null) return "";
		return String.format(this.m_configuration.getIdFormat(), code.getCodeSystem(), code.getCode());
	}
	/**
	 * Parse a person name
	 * @param name The name to parse
	 * @return The parsed name
	 */
	@SuppressWarnings("incomplete-switch")
	public PersonName parseEN(EN en)
	{
		PersonName name = new PersonName();
		// Iterate through parts
		for(ENXP part : en.getParts())
			if(part.getType() != null)
				switch(part.getType().getCode())
				{
					case Family:
						if(name.getFamilyName() == null)
							name.setFamilyName(part.getValue());
						else if(name.getFamilyName2() == null)
							name.setFamilyName2(part.getValue());
						else
							name.setFamilyName2(name.getFamilyName2() + " " + part.getValue());
						break;
					case Given:
						if(name.getGivenName() == null)
							name.setGivenName(part.getValue());
						else if (name.getMiddleName() == null)
							name.setMiddleName(part.getValue());
						else
							name.setMiddleName(name.getMiddleName() + " " + part.getValue());
						break;
					case Prefix:
						if(name.getPrefix() == null)
							name.setPrefix(part.getValue());
						else
							name.setPrefix(part.getValue() + " " + part.getValue());
						break;
						// TODO: Suffix?
				}
			else // This represents a simple name
			{
				if(name.getGivenName() == null)
					name.setGivenName(part.getValue());
				else if(name.getMiddleName() == null)
					name.setMiddleName(part.getValue());
				else {
					name.setMiddleName(name.getMiddleName() + " " + part.getValue());
				}
				
				if(name.getFamilyName() == null)
					name.setFamilyName("?");
				break;
			}
	
		return name;
	}

	/**
	 * Parse an HL7v3 AD into an OpenMRS PersonAddress
	 * @param ad The HL7v3 AD to parse
	 * @return The parsed Address
	 * @throws DocumentImportException 
	 */
	@SuppressWarnings("incomplete-switch")
	public PersonAddress parseAD(AD ad) throws DocumentImportException {
		PersonAddress address = new PersonAddress();
		// Iterate through parts
		for(ADXP part : ad.getPart())
			switch(part.getPartType())
			{
				case AddressLine:
				case StreetAddressLine:
				case AdditionalLocator:
					if(address.getAddress1() == null)
						address.setAddress1(part.getValue());
					else if(address.getAddress2() == null)
						address.setAddress2(part.getValue());
					else if(address.getAddress3() == null)
						address.setAddress3(part.getValue());
					else if(address.getAddress4() == null)
						address.setAddress4(part.getValue());
					else if(address.getAddress5() == null)
						address.setAddress5(part.getValue());
					else if(address.getAddress6() == null)
						address.setAddress6(part.getValue());
					break;
				case Country:
					address.setCountry(part.getValue());
					break;
				case City:
					address.setCityVillage(part.getValue());
					break;
				case State:
					address.setStateProvince(part.getValue());
					break;
				case County:
				case Precinct:
					address.setCountyDistrict(part.getValue());
					break;
				case PostalCode:
					address.setPostalCode(part.getValue());
					break;
			}

		// Is there a simple useable period on here?
		if(ad.getUseablePeriod() != null && ad.getUseablePeriod().getHull() instanceof IVL)
		{
			IVL<TS> useablePeriod = (IVL<TS>)ad.getUseablePeriod().getHull();
			if(useablePeriod.getLow() != null)
				address.setStartDate(useablePeriod.getLow().getDateValue().getTime());
			if(useablePeriod.getHigh() != null)
				address.setEndDate(useablePeriod.getHigh().getDateValue().getTime());
		}
		else if(ad.getUseablePeriod() != null)
			throw new DocumentImportException("Complex GTS instances are not supported for usablePeriod. Please use GTS with IVL");
		return address;
	}

	
	/**
	 * Gets the empty identifier string
	 */
	public String emptyIdString() {
		return this.formatIdentifier(new II());
    }

	/**
	 * Cascades values from source to destination
	 * @throws DocumentImportException 
	 */
	public void cascade(IGraphable source, IGraphable destination, String... propertyNames) throws DocumentImportException
	{

		List<String> traversalsToCopy  = Arrays.asList(propertyNames);
		
		// Find methods
		for(Method m : source.getClass().getMethods())
		{
			
			// Get property annotations
			Property property = m.getAnnotation(Property.class);
			Properties properties = m.getAnnotation(Properties.class);
			if(property == null && properties == null) continue;
			
			// everest annotations are on getters
			if(!m.getName().startsWith("get")) 
				continue;
				
			// Is there another property in destination?
			try {
				Method destinationMethod = destination.getClass().getMethod(m.getName(), null);
            
				if(destinationMethod == null || destinationMethod.invoke(destination, null) != null) // no point can't cascade anyways
					continue;
				
				// Set a context
				FormatterElementContext sourceContext = new FormatterElementContext(source.getClass(), m),
						destinationContext = new FormatterElementContext(destination.getClass(), destinationMethod);
				
				Boolean shouldCopy = false;
				// See if this is an interesting property
				shouldCopy = (sourceContext.getPropertyAnnotation() != null &&
						traversalsToCopy.contains(sourceContext.getPropertyAnnotation().name()) &&
						sourceContext.getPropertyAnnotation().name().equals(destinationContext.getPropertyAnnotation().name()));
				if(sourceContext.getPropertiesAnnotation() != null)
					for(Property prop : sourceContext.getPropertiesAnnotation().value())
						shouldCopy |= (sourceContext.getPropertyAnnotation() != null &&
							traversalsToCopy.contains(sourceContext.getPropertyAnnotation().name()) &&
							sourceContext.getPropertyAnnotation().name().equals(destinationContext.getPropertyAnnotation().name()));
				
				// If properties found the cascade 
				if(shouldCopy)
		                destinationContext.getSetterMethod().invoke(destination, m.invoke(source, null));
            }
			catch(NoSuchMethodException e)
			{
				
			}
            catch (Exception e) {
                throw new DocumentImportException("Could not cascade property values", e);
            }
		}
			
		
	}

	/**
	 * Returns true if the template identifiers in clinicalStatement contains 
	 * the specified templateId
	 */
	public boolean hasTemplateId(InfrastructureRoot cdaObject, II templateId) {
		if(cdaObject == null || cdaObject.getTemplateId() == null)
			return false;
		for(II templId : cdaObject.getTemplateId())
			if(templateId.semanticEquals(templId).toBoolean())
				return true;
		return false;
    }
	
	
}