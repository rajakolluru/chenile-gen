Feature: Tests the demogaurav Service using a REST client.
 
  Scenario: Save the demogaurav first.
    Given that "tenant" equals "tenant0"
    And that "employee" equals "E1"
    When I construct a REST request with header "x-chenile-tenant-id" and value "${tenant}"
    And I construct a REST request with header "x-chenile-eid" and value "${employee}"
    And I POST a REST request to URL "/demogaurav" with payload
    """
    {
      "attribute1": "value-of-attribute1"
	}
	"""
	Then success is true
    And store "$.payload.id" from response to "id"
    # And the REST response key "tenant" is "${tenant}"
    # And the REST response key "createdBy" is "${employee}"

  Scenario: Retrieve the saved demogaurav
    Given that "entity" equals "demogaurav"
    And I construct a REST request with header "x-chenile-tenant-id" and value "tenant0"
    And I GET a REST request to URL "/${entity}/${id}"
    Then success is true
    And the REST response key "id" is "${id}"

  Scenario: Save a demogaurav using an ID that already is determined
  Given that "id" equals "123"
    And I construct a REST request with header "x-chenile-tenant-id" and value "tenant0"
    And I construct a REST request with header "x-chenile-eid" and value "E1"
    And I POST a REST request to URL "/demogaurav" with payload
  """
  {
    "id": "${id}",
    "attribute1": "value-of-attribute1"
  }
  """
    Then success is true
    And the REST response key "id" is "${id}"

  Scenario: Retrieve the saved demogaurav
  Given that "entity" equals "demogaurav"
    And I construct a REST request with header "x-chenile-tenant-id" and value "tenant0"
    And I GET a REST request to URL "/${entity}/${id}"
    Then success is true
    And the REST response key "id" is "${id}"


    