package com.example.mymoo.domain.store.service.Impl;

import com.example.mymoo.domain.account.entity.Account;
import com.example.mymoo.domain.account.exception.AccountException;
import com.example.mymoo.domain.account.exception.AccountExceptionDetails;
import com.example.mymoo.domain.account.repository.AccountRepository;
import com.example.mymoo.domain.store.dto.response.MenuListDTO;
import com.example.mymoo.domain.store.dto.response.StoreDetailDTO;
import com.example.mymoo.domain.store.dto.response.StoreListDTO;
import com.example.mymoo.domain.store.entity.BookMark;
import com.example.mymoo.domain.store.entity.Like;
import com.example.mymoo.domain.store.entity.Menu;
import com.example.mymoo.domain.store.entity.Store;
import com.example.mymoo.domain.store.exception.StoreException;
import com.example.mymoo.domain.store.exception.StoreExceptionDetails;
import com.example.mymoo.domain.store.repository.BookMarkRepository;
import com.example.mymoo.domain.store.repository.LikeRepository;
import com.example.mymoo.domain.store.repository.StoreRepository;
import com.example.mymoo.domain.store.service.StoreService;
import com.example.mymoo.domain.store.util.StoreUtil;
import com.example.mymoo.global.aop.log.LogExecutionTime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
@LogExecutionTime
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final LikeRepository likeRepository;
    private final BookMarkRepository bookMarkRepository;
    private final AccountRepository accountRepository;


    // 위치기반으로 음식점을 조회
    @Transactional(readOnly = true)
    public StoreListDTO getAllStoresByLocation(
            final Double logt,
            final Double lat,
            final int page,
            final int size,
            final Long accountId
    ){
        List<Store> foundStores = storeRepository.findAll();
        Map<Integer, Store> storeMap = new HashMap<>();
        for(Store store : foundStores){
            // 현재 위치로부터 위치를 key store 를 value 로 저장
            storeMap.put(StoreUtil.calculateDistance(logt, lat, store.getLongitude(), store.getLatitude()), store);
        }
        List<Integer> storeList = new ArrayList<>(storeMap.keySet()); // map 에서 keyset 을 뽑아와서 정렬
        storeList.sort(Comparator.naturalOrder());
        List<Store> selectedStores = new ArrayList<>();
        for (int i=page*size ; i<page*size+size ;i++) {
            selectedStores.add(storeMap.get(storeList.get(i))); // 정렬된 순서대로 map 의 value 를 삽입
        }
        List<Like> likes = likeRepository.findAllByAccount_Id(accountId);
        return StoreListDTO.from(foundStores.size(), selectedStores, likes, page, size, false, logt, lat);
    }

    //keyword 를 포함하는 음식점명, 주소를 가진 음식점을 조회
    @Transactional(readOnly = true)
    public StoreListDTO getAllStoresByKeyword(
            final String keyword,
            final Pageable pageable,
            final Long accountId,
            final Double logt,
            final Double lat
    ){
        Slice<Store> storesFoundByKeyword = storeRepository.findAllByNameContainsOrAddressContains(keyword, keyword, pageable);
        List<Store> selectedStores = storesFoundByKeyword.stream().toList();
        List<Like> likes = likeRepository.findAllByAccount_Id(accountId);
        return StoreListDTO.from(storesFoundByKeyword.getNumberOfElements(), selectedStores,
                likes, pageable.getPageNumber(), pageable.getPageSize(), storesFoundByKeyword.hasNext(), logt, lat);
    }

    //음식점 id로 음식점을 조회
    @Transactional(readOnly = true)
    public StoreDetailDTO getStoreById(
            final Long storeId,
            final Long accountId
    ){
        Store found = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreExceptionDetails.STORE_NOT_FOUND));
        Optional<Like> foundLike = likeRepository.findByAccount_IdAndStore_Id(accountId,storeId);
        return StoreDetailDTO.from(found, foundLike.isEmpty());
    }

    //음식점 id로 메뉴를 조회
    @Transactional(readOnly = true)
    public MenuListDTO getMenusByStoreId(
            final Long id
    ){
        Store found = storeRepository.findById(id).orElseThrow(() -> new StoreException(StoreExceptionDetails.STORE_NOT_FOUND));
        List<Menu> foundMenus = found.getMenus();
        return MenuListDTO.from(foundMenus);
    }

    //음식점 좋아요 수정
    public String updateStoreLikeCount(
            final Long storeId,
            final Long accountId
    ){
        Optional<Like> foundLike = likeRepository.findByAccount_IdAndStore_Id(accountId,storeId);
        if (foundLike.isPresent()){
            Like like = foundLike.get();
            Store store = like.getStore();
            store.decrementLikeCount();
            storeRepository.save(store);
            likeRepository.delete(like);
            return "likeCount--";
        }else{
            Store foundStore = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreExceptionDetails.STORE_NOT_FOUND));
            Account foundAccount = accountRepository.findById(accountId).orElseThrow(() -> new AccountException(AccountExceptionDetails.ACCOUNT_NOT_FOUND));
            foundStore.incrementLikeCount();
            likeRepository.save(
                    Like.builder()
                            .account(foundAccount)
                            .store(foundStore)
                            .build());
            return "likeCount++";
        }
    }

    public String updateStoreBookMark(
            final Long storeId,
            final Long accountId
    ) {
        Optional<BookMark> foundBookMark = bookMarkRepository.findByAccount_IdAndStore_Id(accountId, storeId);
        if(foundBookMark.isPresent()){
            bookMarkRepository.delete(foundBookMark.get());
            return "BookMark Removed";
        }else{
            Store foundStore = storeRepository.findById(storeId).orElseThrow(() -> new StoreException(StoreExceptionDetails.STORE_NOT_FOUND));
            Account foundAccount = accountRepository.findById(accountId).orElseThrow(() -> new AccountException(AccountExceptionDetails.ACCOUNT_NOT_FOUND));
            bookMarkRepository.save(
                    BookMark.builder()
                            .account(foundAccount)
                            .store(foundStore)
                            .build());
            return "BookMark Added";
        }
    }

    public StoreListDTO getAllStoresBookMarked(
            final Long accountId,
            final Pageable pageable,
            final Double logt,
            final Double lat
    ){

        Slice<BookMark> foundBookMark = bookMarkRepository.findAllByAccount_Id(accountId, pageable);
        List<Like> likes = likeRepository.findAllByAccount_Id(accountId);
        List<Store> stores = foundBookMark.stream()
                .map(BookMark::getStore)
                .toList();

        return StoreListDTO.from(foundBookMark.getNumberOfElements(), stores,
                likes, pageable.getPageNumber(), pageable.getPageSize(), foundBookMark.hasNext(), logt, lat);
    }
}
