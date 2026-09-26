<%--
  품목 관리 목록 (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>품목 관리 목록</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<script type="text/javascript">
function fn_egov_select_linkPage(pageNo) {
	document.itemListForm.pageIndex.value = pageNo;
	document.itemListForm.submit();
}
function fn_search() {
	document.itemListForm.pageIndex.value = 1;
	document.itemListForm.submit();
}
</script>
</head>
<body>

<form name="itemListForm" action="<c:url value='/asset/item/list.do'/>" method="post" onsubmit="fn_search(); return false;">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
<div class="board">
	<h1>품목 관리 목록</h1>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<div class="search_box" title="검색 조건">
		<ul>
			<li>
				<label for="searchItemTy">유형</label>
				<select id="searchItemTy" name="searchItemTy" title="품목 유형 선택">
					<option value="">전체</option>
					<option value="QTY" <c:if test="${searchVO.searchItemTy == 'QTY'}">selected="selected"</c:if>>수량 비품</option>
					<option value="UNIT" <c:if test="${searchVO.searchItemTy == 'UNIT'}">selected="selected"</c:if>>개별 자산</option>
				</select>
			</li>
			<li>
				<label for="searchUseYn">사용</label>
				<select id="searchUseYn" name="searchUseYn" title="사용 여부 선택">
					<option value="">전체</option>
					<option value="Y" <c:if test="${searchVO.searchUseYn == 'Y'}">selected="selected"</c:if>>사용</option>
					<option value="N" <c:if test="${searchVO.searchUseYn == 'N'}">selected="selected"</c:if>>중지</option>
				</select>
			</li>
			<li>
				<label for="searchKeyword">품목명</label>
				<input class="s_input" id="searchKeyword" name="searchKeyword" type="text" size="35" maxlength="100" title="품목명 입력" value="<c:out value='${searchVO.searchKeyword}'/>">
				<input type="submit" class="s_btn" value="조회" title="조회 버튼" />
				<span class="btn_b"><a href="<c:url value='/asset/item/form.do'/>" title="품목 등록 화면으로 이동">등록</a></span>
			</li>
		</ul>
	</div>

	<table class="board_list">
	<caption>품목 목록</caption>
	<colgroup>
		<col style="width:6%;"><col style="width:15%;"><col><col style="width:9%;"><col style="width:8%;"><col style="width:6%;">
		<col style="width:8%;"><col style="width:7%;"><col style="width:7%;"><col style="width:7%;"><col style="width:6%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">번호</th>
		<th scope="col">품목코드</th>
		<th scope="col">품목명</th>
		<th scope="col">유형</th>
		<th scope="col">분류</th>
		<th scope="col">단위</th>
		<th scope="col">재고/보관</th>
		<th scope="col">확보</th>
		<th scope="col">가용</th>
		<th scope="col">최소</th>
		<th scope="col">사용</th>
	</tr>
	</thead>
	<tbody class="ov">
	<c:if test="${fn:length(resultList) == 0}">
	<tr><td colspan="11">조회된 품목이 없습니다.</td></tr>
	</c:if>
	<c:forEach items="${resultList}" var="row" varStatus="status">
	<tr>
		<td><c:out value="${(searchVO.pageIndex - 1) * searchVO.pageUnit + status.count}"/></td>
		<td><c:out value="${row.itemCode}"/></td>
		<td class="left"><a href="<c:url value='/asset/item/form.do'><c:param name='itemCode' value='${row.itemCode}'/></c:url>"><c:out value="${row.itemNm}"/></a></td>
		<td><c:choose><c:when test="${row.itemTy == 'UNIT'}">개별 자산</c:when><c:otherwise>수량 비품</c:otherwise></c:choose></td>
		<td><c:out value="${row.clCodeNm}"/></td>
		<td><c:out value="${row.unitCodeNm}"/></td>
		<td><c:choose><c:when test="${row.itemTy == 'UNIT'}"><c:out value="${row.storedQty}"/></c:when><c:otherwise><c:out value="${row.stockQty}"/></c:otherwise></c:choose></td>
		<td><c:choose><c:when test="${row.itemTy == 'UNIT'}"><c:out value="${row.reservedQty}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
		<td><c:choose><c:when test="${row.availQty < row.minQty}"><strong title="최소 수량 미달"><c:out value="${row.availQty}"/></strong></c:when><c:otherwise><c:out value="${row.availQty}"/></c:otherwise></c:choose></td>
		<td><c:out value="${row.minQty}"/></td>
		<td><c:choose><c:when test="${row.useAt == 'Y'}">사용</c:when><c:otherwise>중지</c:otherwise></c:choose></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="pagination">
		<ul>
		<ui:pagination paginationInfo="${paginationInfo}" type="image" jsFunction="fn_egov_select_linkPage"/>
		</ul>
	</div>
</div>
<input name="pageIndex" type="hidden" value="<c:out value='${searchVO.pageIndex}'/>">
</form>

</body>
</html>
