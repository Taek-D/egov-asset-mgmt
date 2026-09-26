<%--
  신청서 목록(내 신청·승인 대기함·부서 신청 조회·지급 관리·전체 신청 조회 공용). 범위는 서비스가 정한다.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title><c:out value="${listTitle}"/></title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<script type="text/javascript">
function fn_egov_select_linkPage(pageNo) {
	document.reqListForm.pageIndex.value = pageNo;
	document.reqListForm.submit();
}
function fn_search() {
	document.reqListForm.pageIndex.value = 1;
	document.reqListForm.submit();
}
</script>
</head>
<body>

<form name="reqListForm" action="<c:url value='${listAction}'/>" method="post" onsubmit="fn_search(); return false;">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
<div class="board">
	<h1><c:out value="${listTitle}"/></h1>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>
	<c:if test="${not empty listError}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${listError}"/></p>
	</c:if>

	<c:if test="${empty listError}">
	<div class="search_box" title="검색 조건">
		<ul>
			<c:if test="${scope != 'INBOX'}">
			<li>
				<label for="searchSttus">상태</label>
				<select id="searchSttus" name="searchSttus" title="상태 선택">
					<option value="">전체</option>
					<c:forEach items="${sttusList}" var="s">
					<option value="${s}" <c:if test="${searchVO.searchSttus == s}">selected="selected"</c:if>><c:out value="${sttusNm[s]}"/></option>
					</c:forEach>
				</select>
			</li>
			</c:if>
			<li>
				<label for="searchReqNo">신청번호</label>
				<input class="s_input" id="searchReqNo" name="searchReqNo" type="text" size="22" maxlength="20" title="신청번호 입력(일부)" value="<c:out value='${searchVO.searchReqNo}'/>">
				<input type="submit" class="s_btn" value="조회" title="조회 버튼" />
				<c:if test="${scope == 'MY'}"><span class="btn_b"><a href="<c:url value='/asset/req/form.do'/>" title="신청서 작성 화면으로 이동">신청서 작성</a></span></c:if>
			</li>
		</ul>
	</div>

	<table class="board_list">
	<caption><c:out value="${listTitle}"/></caption>
	<colgroup>
		<col style="width:6%;"><col style="width:17%;"><col style="width:12%;"><col style="width:12%;"><col><col style="width:13%;"><col style="width:14%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">번호</th>
		<th scope="col">신청번호</th>
		<th scope="col">신청자</th>
		<th scope="col">신청 당시 부서</th>
		<th scope="col">품목</th>
		<th scope="col">상태</th>
		<th scope="col">신청 시각</th>
	</tr>
	</thead>
	<tbody class="ov">
	<c:if test="${fn:length(resultList) == 0}">
	<tr><td colspan="7">조회된 신청이 없습니다.</td></tr>
	</c:if>
	<c:forEach items="${resultList}" var="row" varStatus="status">
	<tr>
		<td><c:out value="${(searchVO.pageIndex - 1) * searchVO.pageUnit + status.count}"/></td>
		<td class="left">
			<c:choose>
			<c:when test="${rowLink == 'approve' && row.sttus == 'PENDING'}">
				<a href="<c:url value='/asset/approve/form.do'><c:param name='reqNo' value='${row.reqNo}'/></c:url>" title="승인 화면으로 이동"><c:out value="${row.reqNo}"/></a>
			</c:when>
			<c:when test="${rowLink == 'deliver' && row.sttus == 'APPROVED'}">
				<a href="<c:url value='/asset/deliver/form.do'><c:param name='reqNo' value='${row.reqNo}'/></c:url>" title="지급 화면으로 이동"><c:out value="${row.reqNo}"/></a>
			</c:when>
			<c:otherwise>
				<a href="<c:url value='/asset/req/detail.do'><c:param name='reqNo' value='${row.reqNo}'/></c:url>" title="신청 상세로 이동"><c:out value="${row.reqNo}"/></a>
			</c:otherwise>
			</c:choose>
		</td>
		<td><c:out value="${row.applcntNm}"/> (<c:out value="${row.applcntId}"/>)</td>
		<td><c:out value="${row.orgnztNm}"/></td>
		<td class="left"><c:out value="${row.firstItemNm}"/><c:if test="${row.lineCnt > 1}"> 외 <c:out value="${row.lineCnt - 1}"/>건</c:if></td>
		<td><c:out value="${sttusNm[row.sttus]}"/><c:if test="${row.noApprover}"><br/><strong>승인자 없음</strong></c:if></td>
		<td><fmt:formatDate value="${row.reqPnttm}" pattern="yyyy-MM-dd HH:mm"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="pagination">
		<ul>
		<ui:pagination paginationInfo="${paginationInfo}" type="image" jsFunction="fn_egov_select_linkPage"/>
		</ul>
	</div>
	</c:if>
</div>
<input name="pageIndex" type="hidden" value="<c:out value='${searchVO.pageIndex}'/>">
</form>

</body>
</html>
