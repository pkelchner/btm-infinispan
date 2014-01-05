btm-infinispan
==============

Integrates [Infinispan](http://infinispan.org/) with [Bitronix](https://github.com/bitronix/btm/).
                                                                                                
```java
EmbeddedCacheManager cacheManager = ...;

Configuration conf = new ConfigurationBuilder()
    .read(cacheManager.getDefaultCacheConfiguration())
    .transaction()
        .transactionManagerLookup(new BitronixTransactionManagerLookup())
        .transactionMode(TransactionMode.TRANSACTIONAL)
        .useSynchronization(false)
        .recovery().enable()
    .build();

cacheManager.defineConfiguration("xaCache", conf);
Cache cache = cacheManager.getCache("xaCache");

UserTransaction tx = ...;
tx.begin();
cache.put("test", "test");
tx.rollback();

System.out.println(cache.containsKey("test")); // false
```
