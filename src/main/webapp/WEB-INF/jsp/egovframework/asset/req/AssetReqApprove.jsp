<%--
  승인 화면: 모든 품목의 처리(승인 수량 또는 반려+사유)를 정해 한 번에 제출. 미결로 남기고 제출할 수 없다.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>신청 승인</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="wTableFrm">
	<h2>신청 승인</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<%@ include file="AssetReqHeader.jspf" %>

	<p style="clear:both; margin-top:10px;">품목마다 승인(승인 수량 1 이상, 신청 수량 이하) 또는 반려(사유 필수)를 정한 뒤 한 번에 제출합니다.
	승인한 품목 가운데 하나라도 가용이 모자라면 아무것도 처리되지 않고 모자란 품목을 알려 줍니다. 모두 반려하면 신청서는 '반려'가 됩니다.</p>

	<form name="approveForm" action="<c:url value='/asset/approve/submit.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="reqNo" value="<c:out value='${req.reqNo}'/>"/>
	<table class="board_list">
	<caption>품목별 처리</caption>
	<colgroup><col><col style="width:9%;"><col style="width:9%;"><col style="width:16%;"><col style="width:13%;"><col style="width:25%;"></colgroup>
	<thead>
	<tr>
		<th scope="col">품목</th>
		<th scope="col">신청 수량</th>
		<th scope="col">현재 가용</th>
		<th scope="col">처리</th>
		<th scope="col">승인 수량</th>
		<th scope="col">반려 사유</th>
	</tr>
	</thead>
	<tbody>
	<c:forEach items="${req.lines}" var="ln" varStatus="st">
	<c:set var="i" value="${st.index}"/>
	<tr>
		<td class="left">
			<input type="hidden" name="lines[${i}].itemCode" value="<c:out value='${ln.itemCode}'/>"/>
			<c:out value="${ln.itemNm}"/><c:if test="${ln.useAt != 'Y'}"> (사용 중지)</c:if>
		</td>
		<td><c:out value="${ln.reqQty}"/></td>
		<td><c:out value="${ln.availQty}"/></td>
		<td>
			<input type="radio" id="ok${i}" name="lines[${i}].lineSttus" value="APPROVED" required="required" <c:if test="${ln.lineSttus == 'APPROVED'}">checked="checked"</c:if>/>
			<label for="ok${i}"><c:out value="${ln.itemNm}"/> 승인</label><br/>
			<input type="radio" id="no${i}" name="lines[${i}].lineSttus" value="REJECTED" <c:if test="${ln.lineSttus == 'REJECTED'}">checked="checked"</c:if>/>
			<label for="no${i}"><c:out value="${ln.itemNm}"/> 반려</label>
		</td>
		<td>
			<label for="aprvQty${i}" style="position:absolute; left:-9999px;"><c:out value="${ln.itemNm}"/> 승인 수량</label>
			<input type="number" id="aprvQty${i}" name="lines[${i}].aprvQty" min="1" max="${ln.reqQty}" size="5" title="승인 수량(1~${ln.reqQty})"
				value="<c:out value='${empty ln.aprvQty ? ln.reqQty : ln.aprvQty}'/>"/>
		</td>
		<td>
			<label for="rejectResn${i}" style="position:absolute; left:-9999px;"><c:out value="${ln.itemNm}"/> 반려 사유</label>
			<input type="text" id="rejectResn${i}" name="lines[${i}].rejectResn" size="25" maxlength="500" title="반려 사유(반려할 때 필수)" value="<c:out value='${ln.rejectResn}'/>"/>
		</td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
	<div class="btn">
		<input type="submit" class="s_submit" value="처리 제출" title="모든 품목의 처리를 한 번에 제출"/>
		<span class="btn_s"><a href="<c:url value='/asset/approve/inbox.do'/>" title="승인 대기함으로 이동">승인 대기함</a></span>
		<span class="btn_s"><a href="<c:url value='/asset/req/detail.do'><c:param name='reqNo' value='${req.reqNo}'/></c:url>" title="신청 상세로 이동">상세</a></span>
	</div>
	</form>
</div>

</body>
</html>
