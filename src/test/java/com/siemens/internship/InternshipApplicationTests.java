package com.siemens.internship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import com.siemens.internship.service.ItemService;
import com.siemens.internship.validator.EmailValidatorRegex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternshipApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	ItemService service;

	@Autowired
	private EmailValidatorRegex validator;

	@AfterEach
	void cleanup() {
		itemRepository.deleteAll();
	}


	@Test
	void contextLoads() {
		assertThat(itemRepository).isNotNull();
	}


	// test for creating an item (HTTP 201)
	@Test
	void createItemReturns201() throws Exception {
		Item in = new Item(null, "TestName", "Descriere", "NEW", "ana_maria12@yahoo.com");
		String payload = objectMapper.writeValueAsString(in);

		mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/items/")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("TestName"));
	}


	// test for GET empty list when no items exist
	@Test
	void getAllItemsEmpty() throws Exception {
		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}


	// test for GET list with items present
	@Test
	void getAllItemsPopulated() throws Exception {
		itemRepository.save(new Item(null,"X","","NEW","x@y.com"));
		itemRepository.save(new Item(null,"Y","","NEW","y@z.com"));

		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name", is("X")))
				.andExpect(jsonPath("$[1].name", is("Y")));
	}


	// test for GET non-existing item returns 404
	@Test
	void getItemByIdNotFound() throws Exception {
		mockMvc.perform(get("/api/items/11522"))
				.andExpect(status().isNotFound());
	}


	// test for successful item update
	@Test
	void updateItemSuccess() throws Exception {
		Item saved = itemRepository.save(new Item(null,"A","D","NEW","a@b.com"));
		Item toUpdate = new Item(null,"B","D2","DONE","b@c.com");

		mockMvc.perform(put("/api/items/" + saved.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(toUpdate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("B"))
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.email").value("b@c.com"));
	}


	// test for updating non-existing item returns 404
	@Test
	void updateItemNotFound() throws Exception {
		mockMvc.perform(put("/api/items/999")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new Item())))
				.andExpect(status().isNotFound());
	}


	// test for deleting existing item and verifying removal
	@Test
	void deleteItemSuccess() throws Exception {
		Item saved = itemRepository.save(new Item(null,"X",null,"NEW","x@y.com"));
		mockMvc.perform(delete("/api/items/" + saved.getId()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/items/" + saved.getId()))
				.andExpect(status().isNotFound());
	}


	// test for deleting non-existing item returns 404
	@Test
	void deleteItemNotFound() throws Exception {
		mockMvc.perform(delete("/api/items/12345"))
				.andExpect(status().isNotFound());
	}


	// test for validation errors on create
	@Test
	void createValidationFails() throws Exception {
		Item bad = new Item(null, "", null, "", "");
		String payload = objectMapper.writeValueAsString(bad);

		mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.name", containsString("should not be blank")))
				.andExpect(jsonPath("$.status", containsString("should not be blank")))
				.andExpect(jsonPath("$.email", containsString("should not be blank")));
	}


	// test for valid email patterns
	@Test
	void validEmails() {
		String[] validEmails = {
				"user@example.com",
				"firstname.lastname@domain.co.uk",
				"user+tag@sub.domain.com",
				"user_name-123@domain.io",
				"\"quoted@local\"@example.com"
		};

		for (String email : validEmails) {
			Item it = new Item(1L, "n", "d", "NEW", email);
			assertDoesNotThrow(() -> validator.validate(it),
					() -> "Expected valid email not to throw: " + email);
		}
	}


	// test for invalid email patterns
	@Test
	void invalidEmails() {
		String[] invalidEmails = {
				"",
				"plainaddress",
				"@no-local.com",
				"user@.com",
				null
		};

		for (String email : invalidEmails) {
			Item it = new Item(1L, "n", "d", "NEW", email);
			assertThrows(IllegalArgumentException.class,
					() -> validator.validate(it),
					"Expected invalid email to throw: " + email);
		}
	}


	// test for update with invalid email returns 400
	@Test
	void updateInvalidEmail() throws Exception {
		Item saved = itemRepository.save(new Item(null, "A","D","NEW","a@b.com"));
		Item badEmail = new Item(null, "A","D","NEW","not-an-email");
		String payload = objectMapper.writeValueAsString(badEmail);

		mockMvc.perform(put("/api/items/" + saved.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.email", containsString("valid email")));
	}


	// test for async endpoint processing items
	@Test
	void processEndpointSuccess() throws Exception {
		itemRepository.save(new Item(null, "Ana","descriere1","NEW","ana@gmail.com"));
		itemRepository.save(new Item(null, "Maria","descriere2","NEW","mariaa1234@gmail.com"));

		MvcResult mvcResult = mockMvc.perform(get("/api/items/process"))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].status", everyItem(is("PROCESSED"))));
	}


	// test for async endpoint returns empty list when no items
	@Test
	void processEndpointEmpty() throws Exception {
		MvcResult mvc = mockMvc.perform(get("/api/items/process"))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(mvc))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}


	// test for service async call returns empty list
	@Test
	void asyncProcessEmpty() throws Exception {
		itemRepository.deleteAll();
		List<Item> processed = service.processItemsAsync().get(5, TimeUnit.SECONDS);
		assertThat(processed).isEmpty();
	}


	// test for service async call processes two items
	@Test
	void asyncProcessTwoItems() throws Exception {
		itemRepository.deleteAll();
		itemRepository.save(new Item(null,"A","","NEW","a@b.com"));
		itemRepository.save(new Item(null,"B","","NEW","b@c.com"));

		List<Item> processed = service.processItemsAsync().get(5, TimeUnit.SECONDS);
		assertThat(processed).hasSize(2);
		assertThat(processed).extracting(Item::getStatus).containsOnly("PROCESSED");
	}


	// test for service async call failing when repo throws exception
	@Test
	void asyncProcessRepoError() throws Exception {
		ItemRepository mockRepo = mock(ItemRepository.class);
		SyncTaskExecutor syncExecutor = new SyncTaskExecutor();

		when(mockRepo.findAllIds()).thenReturn(List.of(1L));
		when(mockRepo.findById(1L)).thenThrow(new RuntimeException("uh-oh"));

		ItemService svc = new ItemService(mockRepo, syncExecutor);
		CompletableFuture<List<Item>> futureL = svc.processItemsAsync();

		assertThrows(ExecutionException.class,
				() -> futureL.get(2, TimeUnit.SECONDS),
				"Expected the batch future to be completed exceptionally");
	}
}