package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Before, we had ArrayList and int counter, so threads could clash,
 * we fired off async tasks with CompletableFuture.runAsync but returned the list immediately (incomplete results),
 * only printed InterruptedException and used a static pool without Spring support.
 *
 * What I changed:
 * - I used Collections.synchronizedList and AtomicInteger to guard shared data across threads.
 * - I used CompletableFuture.runAsync(..., executor) to schedule each task on our Spring-configured pool ('itemExecutor')
 * - I collected all futures and used CompletableFuture.allOf(...) to wait for every task to finish
 * - Used exceptionally() to catch and rethrow errors so failures bubble up to callers
 * - Restored interrupt flag and threw an unchecked exception on interruption
 * - I added SLF4J logging for progress and errors
 * - Cleared/reset shared state at the start and returned a copy of results to avoid exposing internal lists
 *
 * I based the async flow on Baeldung’s guides:
 *   - Guide to CompletableFuture: https://www.baeldung.com/java-completablefuture
 *   - Working with Exceptions in CompletableFuture: https://www.baeldung.com/java-exceptions-completablefuture
 */


@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);
    private final ItemRepository itemRepository;
    private final Executor executor;

    private final List<Item> processedItems = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger processedCount = new AtomicInteger(0);

    public ItemService(ItemRepository itemRepository, @Qualifier("itemExecutor") Executor executor){
        this.itemRepository = itemRepository;
        this.executor = executor;
    }

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }


    /**
     * Your Tasks
     * Identify all concurrency and asynchronous programming issues in the code
     * Fix the implementation to ensure:
     * All items are properly processed before the CompletableFuture completes
     * Thread safety for all shared state
     * Proper error handling and propagation
     * Efficient use of system resources
     * Correct use of Spring's @Async annotation
     * Add appropriate comments explaining your changes and why they fix the issues
     * Write a brief explanation of what was wrong with the original implementation
     *
     * Hints
     * Consider how CompletableFuture composition can help coordinate multiple async operations
     * Think about appropriate thread-safe collections
     * Examine how errors are handled and propagated
     * Consider the interaction between Spring's @Async and CompletableFuture
     */
    @Async("itemExecutor")
    public CompletableFuture <List<Item>> processItemsAsync() {

        processedItems.clear();
        processedCount.set(0);

        List<Long> itemIds = itemRepository.findAllIds();

        CompletableFuture<?>[] futures = itemIds.stream().map(
            id -> CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100);

                    Item item = itemRepository.findById(id).orElse(null);
                    if (item == null) {
                        return;
                    }
                    processedCount.incrementAndGet();

                    item.setStatus("PROCESSED");
                    itemRepository.save(item);
                    processedItems.add(item);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // System.out.println("Error: " + e.getMessage());
                    throw new IllegalStateException("Interrupted ", e);
                }
            }, executor)).toArray(CompletableFuture[]::new);


        return CompletableFuture.allOf(futures)
                .<List<Item>>thenApply(s -> {
                    log.info("Finished processing {} items.", processedCount.get());
                    return new ArrayList<>(processedItems);
                })
                .exceptionally(ex -> {
                    log.error("Batch processing failed!", ex.getCause());
                    throw new RuntimeException("Failed to process items!", ex.getCause());
                });
    }
}