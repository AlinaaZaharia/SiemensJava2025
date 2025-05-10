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


	@Test
	void createItem_shouldReturn201_andLocationHeader() throws Exception {
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


	@Test
	void getAllItems_whenEmpty_shouldReturnEmptyList() throws Exception {
		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}


	@Test
	void getAllItems_whenNotEmpty_shouldReturnList() throws Exception {
		itemRepository.save(new Item(null,"X","","NEW","x@y.com"));
		itemRepository.save(new Item(null,"Y","","NEW","y@z.com"));

		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name", is("X")))
				.andExpect(jsonPath("$[1].name", is("Y")));
	}


	@Test
	void getItemById_notFound_shouldReturn404() throws Exception {
		mockMvc.perform(get("/api/items/11522"))
				.andExpect(status().isNotFound());
	}


	@Test
	void updateItem_success() throws Exception {
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


	@Test
	void updateItem_notFound_shouldReturn404() throws Exception {
		mockMvc.perform(put("/api/items/999")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new Item())))
				.andExpect(status().isNotFound());
	}


	@Test
	void deleteItem_success() throws Exception {
		Item saved = itemRepository.save(new Item(null,"X",null,"NEW","x@y.com"));
		mockMvc.perform(delete("/api/items/" + saved.getId()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/items/" + saved.getId()))
				.andExpect(status().isNotFound());
	}


	@Test
	void deleteItem_notFound_shouldReturn404() throws Exception {
		mockMvc.perform(delete("/api/items/12345"))
				.andExpect(status().isNotFound());
	}


	@Test
	void validationFailures_onCreate_shouldReturn400_andFieldErrors() throws Exception {
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


	@Test
	void updateItem_withInvalidEmail_shouldReturn400_andFieldError() throws Exception {
		Item saved = itemRepository.save(new Item(null, "A","D","NEW","a@b.com"));
		Item badEmail = new Item(null, "A","D","NEW","not-an-email");
		String payload = objectMapper.writeValueAsString(badEmail);

		mockMvc.perform(put("/api/items/" + saved.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.email", containsString("valid email")));
	}


	@Test
	void processItemsEndpoint_shouldReturnProcessedStatuses() throws Exception {
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


	@Test
	void processItemsEndpoint_whenNoItems_shouldReturnEmptyList() throws Exception {
		MvcResult mvc = mockMvc.perform(get("/api/items/process"))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(mvc))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}


	@Test
	void processItemsAsync_withNoItems_returnsEmptyList() throws Exception {
		itemRepository.deleteAll();
		List<Item> processed = service.processItemsAsync().get(5, TimeUnit.SECONDS);
		assertThat(processed).isEmpty();
	}


	@Test
	void processItemsAsync_withTwoItems_returnsBothProcessed() throws Exception {
		itemRepository.deleteAll();
		itemRepository.save(new Item(null,"A","","NEW","a@b.com"));
		itemRepository.save(new Item(null,"B","","NEW","b@c.com"));

		List<Item> processed = service.processItemsAsync().get(5, TimeUnit.SECONDS);
		assertThat(processed).hasSize(2);
		assertThat(processed).extracting(Item::getStatus).containsOnly("PROCESSED");
	}


	@Test
	void processItemsAsync_whenRepoThrows_failsFuture() throws Exception {
		ItemRepository mockRepo = mock(ItemRepository.class);
		SyncTaskExecutor syncExecutor = new SyncTaskExecutor();

		when(mockRepo.findAllIds()).thenReturn(List.of(1L));
		when(mockRepo.findById(1L)).thenThrow(new RuntimeException("uh-oh"));

		ItemService svc = new ItemService(mockRepo, syncExecutor);
		CompletableFuture<List<Item>> fut = svc.processItemsAsync();

		assertThrows(ExecutionException.class,
				() -> fut.get(2, TimeUnit.SECONDS),
				"Expected the batch future to be completed exceptionally");
	}
}